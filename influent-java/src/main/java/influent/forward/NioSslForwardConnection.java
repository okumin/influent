/*
 * Copyright 2016 okumin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package influent.forward;

import influent.exception.InfluentIOException;
import influent.internal.msgpack.MsgpackStreamUnpacker;
import influent.internal.nio.NioAttachment;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioTcpChannel;
import influent.internal.nio.NioTcpConfig;
import influent.internal.nio.NioTcpPlaintextChannel;
import influent.internal.util.ThreadSafeQueue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A connection for SSL/TLS forward protocol. */
final class NioSslForwardConnection implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioSslForwardConnection.class);
  private static final String ACK_KEY = "ack";

  private final NioTcpChannel channel;
  private final NioEventLoop eventLoop;
  private final ForwardCallback callback;
  private final SSLEngine engine;
  private final MsgpackStreamUnpacker unpacker;
  private final MsgpackForwardRequestDecoder decoder;

  private final ThreadSafeQueue<ByteBuffer> responses = new ThreadSafeQueue<>();

  // Prepare a ByteBuffer with sufficient size
  private ByteBuffer inboundNetworkBuffer = ByteBuffer.allocate(1024 * 1024);
  private final Queue<ByteBuffer> outboundNetworkBuffers = new LinkedList<>();

  NioSslForwardConnection(
      final NioTcpChannel channel,
      final NioEventLoop eventLoop,
      final ForwardCallback callback,
      final SSLEngine engine,
      final MsgpackStreamUnpacker unpacker,
      final MsgpackForwardRequestDecoder decoder) {
    this.channel = channel;
    this.eventLoop = eventLoop;
    this.callback = callback;
    this.engine = engine;
    this.unpacker = unpacker;
    this.decoder = decoder;
    inboundNetworkBuffer.position(inboundNetworkBuffer.limit());
  }

  NioSslForwardConnection(
      final NioTcpChannel channel,
      final NioEventLoop eventLoop,
      final ForwardCallback callback,
      final SSLEngine engine,
      final long chunkSizeLimit) {
    this(
        channel,
        eventLoop,
        callback,
        engine,
        new MsgpackStreamUnpacker(chunkSizeLimit),
        new MsgpackForwardRequestDecoder());
  }

  /**
   * Constructs a new {@code NioSslForwardConnection}.
   *
   * @param socketChannel the inbound channel
   * @param eventLoop the {@code NioEventLoop} to which this {@code NioSslForwardConnection} belongs
   * @param callback the callback to handle requests
   * @param chunkSizeLimit the allowable size of a chunk
   * @param tcpConfig the {@code NioTcpConfig}
   * @throws InfluentIOException if some IO error occurs
   */
  NioSslForwardConnection(
      final SocketChannel socketChannel,
      final NioEventLoop eventLoop,
      final ForwardCallback callback,
      final SSLEngine engine,
      final long chunkSizeLimit,
      final NioTcpConfig tcpConfig) {
    this(
        NioTcpPlaintextChannel.open(socketChannel, tcpConfig),
        eventLoop,
        callback,
        engine,
        chunkSizeLimit);

    channel.register(eventLoop, true, false, this);
  }

  /**
   * Handles a write event.
   *
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onWritable() {
    if (!handshake()) {
      if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
        channel.enableOpWrite(eventLoop);
      }
      return;
    }

    while (responses.nonEmpty()) {
      final ByteBuffer head = responses.dequeue();
      wrapAndSend(head);
    }
    if (!channel.isOpen()) {
      close();
    }
  }

  /**
   * Handles a read event.
   *
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onReadable() {
    if (!handshake()) {
      if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
        channel.enableOpWrite(eventLoop);
      }
      return;
    }

    receiveRequests();
    if (!channel.isOpen()) {
      close();
    }
  }

  private void receiveRequests() {
    // TODO: optimize
    final Supplier<ByteBuffer> supplier =
        () -> {
          final ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
          receiveAndUnwrap(buffer);
          buffer.flip();
          if (!buffer.hasRemaining()) {
            return null;
          }
          return buffer;
        };
    unpacker.feed(supplier, channel);
    while (unpacker.hasNext()) {
      try {
        decoder
            .decode(unpacker.next())
            .ifPresent(
                result -> {
                  logger.debug(
                      "Received a forward request from {}. chunk_id = {}",
                      channel.getRemoteAddress(),
                      result.getOption());
                  callback
                      .consume(result.getStream())
                      .thenRun(
                          () -> {
                            // Executes on user's callback thread since the queue never block.
                            result.getOption().getChunk().ifPresent(chunk -> completeTask(chunk));
                            logger.debug("Completed the task. chunk_id = {}.", result.getOption());
                          });
                });
      } catch (final IllegalArgumentException e) {
        logger.error(
            "Received an invalid message. remote address = " + channel.getRemoteAddress(), e);
      }
    }
  }

  // This method is thread-safe.
  private void completeTask(final String chunk) {
    try {
      final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
      packer.packMapHeader(1);
      packer.packString(ACK_KEY);
      packer.packString(chunk);
      final ByteBuffer buffer = packer.toMessageBuffer().sliceAsByteBuffer();
      responses.enqueue(buffer);
      channel.enableOpWrite(eventLoop);
    } catch (final IOException e) {
      logger.error("Failed packing. chunk = " + chunk, e);
    }
  }

  // true when the handshake is completed
  private boolean handshake() {
    final SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
    logger.debug("Current handshake status: " + handshakeStatus);
    if (!isHandshaking(handshakeStatus)) {
      return true;
    }

    switch (handshakeStatus) {
      case NEED_UNWRAP:
        return receiveAndUnwrap(ByteBuffer.allocate(1024 * 1024)) && handshake();
      case NEED_WRAP:
        return wrapAndSend(ByteBuffer.allocate(0)) && handshake();
      case NEED_TASK:
        while (true) {
          final Runnable task = engine.getDelegatedTask();
          if (task == null) {
            break;
          }
          task.run();
        }
        return handshake();
      case FINISHED:
      case NOT_HANDSHAKING:
      default:
        throw new AssertionError();
    }
  }

  private boolean wrapAndSend(final ByteBuffer src) {
    try {
      final ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
      final SSLEngineResult result = engine.wrap(src, buffer);
      switch (result.getStatus()) {
        case OK:
          break;
        case CLOSED:
          close();
          break;
        case BUFFER_OVERFLOW:
        case BUFFER_UNDERFLOW:
        default:
          throw new AssertionError();
      }

      buffer.flip();
      if (buffer.hasRemaining()) {
        outboundNetworkBuffers.add(buffer);
      }
      while (!outboundNetworkBuffers.isEmpty()) {
        final ByteBuffer head = outboundNetworkBuffers.peek();
        if (!channel.write(head)) {
          break;
        }
        if (!head.hasRemaining()) {
          outboundNetworkBuffers.poll();
        }
      }
      if (outboundNetworkBuffers.isEmpty()) {
        channel.disableOpWrite(eventLoop);
      }
      return outboundNetworkBuffers.isEmpty();
    } catch (final SSLException e) {
      throw new InfluentIOException("Illegal SSL/TLS processing was detected.", e);
    } catch (final ReadOnlyBufferException | IllegalArgumentException | IllegalStateException e) {
      throw new AssertionError(e);
    }
  }

  private boolean receiveAndUnwrap(final ByteBuffer dst) {
    try {
      if (!inboundNetworkBuffer.hasRemaining()) {
        inboundNetworkBuffer.clear();
        inboundNetworkBuffer.mark();
      } else {
        inboundNetworkBuffer.mark();
        inboundNetworkBuffer.position(inboundNetworkBuffer.limit());
        inboundNetworkBuffer.limit(inboundNetworkBuffer.capacity());
      }
      final boolean isRead = channel.read(inboundNetworkBuffer);
      inboundNetworkBuffer.limit(inboundNetworkBuffer.position());
      inboundNetworkBuffer.reset();
      if (!inboundNetworkBuffer.hasRemaining()) {
        return false;
      }
      while (inboundNetworkBuffer.hasRemaining()) {
        final int start = dst.position();
        final SSLEngineResult result = engine.unwrap(inboundNetworkBuffer, dst);
        switch (result.getStatus()) {
          case OK:
            if (dst.position() == start) {
              return true;
            }
            break;
          case BUFFER_UNDERFLOW:
            return isRead;
          case CLOSED:
            close();
            if (dst.position() == start) {
              return false;
            }
            break;
          case BUFFER_OVERFLOW:
          default:
            throw new AssertionError();
        }
      }
      return true;
    } catch (final SSLException e) {
      throw new InfluentIOException("Illegal SSL/TLS processing was detected.", e);
    } catch (final ReadOnlyBufferException | IllegalArgumentException | IllegalStateException e) {
      throw new AssertionError(e);
    }
  }

  private static boolean isHandshaking(final SSLEngineResult.HandshakeStatus status) {
    return status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        && status != SSLEngineResult.HandshakeStatus.FINISHED;
  }

  @Override
  public void close() {
    // TODO: graceful stop
    channel.close();
    logger.debug("NioSslForwardConnection bound with {} closed.", channel.getRemoteAddress());
  }

  @Override
  public String toString() {
    return "NioSslForwardConnection(" + channel.getRemoteAddress() + ")";
  }
}
