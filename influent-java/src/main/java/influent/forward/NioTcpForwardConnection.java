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
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A connection for forward protocol.
 */
final class NioTcpForwardConnection extends NioForwardConnection implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioTcpForwardConnection.class);

  NioTcpForwardConnection(final NioTcpChannel channel, final NioEventLoop eventLoop,
                          final ForwardCallback callback, final MsgpackStreamUnpacker unpacker,
                          final MsgpackForwardRequestDecoder decoder, final ForwardSecurity security) {
    super(channel, eventLoop, callback, unpacker, decoder, security);
  }

  NioTcpForwardConnection(final NioTcpChannel channel, final NioEventLoop eventLoop,
                          final ForwardCallback callback, final long chunkSizeLimit, final ForwardSecurity security) {
    this(channel, eventLoop, callback, new MsgpackStreamUnpacker(chunkSizeLimit),
        new MsgpackForwardRequestDecoder(), security);
  }

  /**
   * Constructs a new {@code NioTcpForwardConnection}.
   *
   * @param socketChannel the inbound channel
   * @param eventLoop the {@code NioEventLoop} to which this {@code NioTcpForwardConnection} belongs
   * @param callback the callback to handle requests
   * @param chunkSizeLimit the allowable size of a chunk
   * @param tcpConfig the {@code NioTcpConfig}
   * @throws InfluentIOException if some IO error occurs
   */
  NioTcpForwardConnection(final SocketChannel socketChannel, final NioEventLoop eventLoop,
                          final ForwardCallback callback, final long chunkSizeLimit, final NioTcpConfig tcpConfig,
                          final ForwardSecurity security) {
    this(new NioTcpChannel(socketChannel, tcpConfig), eventLoop, callback, chunkSizeLimit, security);

    if (this.security.isEnabled()) {
      state = ConnectionState.HELO;
      channel.register(eventLoop, false, true, this);
      responses.enqueue(generateHelo());
    } else {
      state = ConnectionState.ESTABLISHED;
      channel.register(eventLoop, true, false, this);
    }
  }

  /**
   * Handles a write event.
   *
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onWritable() {
    if (sendResponses()) {
      channel.disableOpWrite(eventLoop);
      if (state == ConnectionState.HELO) {
        state = ConnectionState.PINGPONG;
        channel.enableOpRead(eventLoop);
        // TODO disconnect after writing failed PONG
      }
    }
  }

  private boolean sendResponses() {
    // TODO: gathering
    while (responses.nonEmpty()) {
      final ByteBuffer head = responses.peek();
      channel.write(head);
      if (head.hasRemaining()) {
        return false;
      }
      responses.dequeue();
    }
    return true;
  }

  /**
   * Handles a read event.
   *
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onReadable() {
    switch (state) {
      case PINGPONG:
        receivePing(result -> {
          responses.enqueue(generatePong(result));
          channel.enableOpWrite(eventLoop);
          state = ConnectionState.ESTABLISHED;
        });
        break;
      case ESTABLISHED:
        receiveRequests();
        break;
    }
    if (!channel.isOpen()) {
      close();
    }
  }

  private void receivePing(Consumer<CheckPingResult> checkPingResultConsumer) {
    // TODO: optimize
    final Supplier<ByteBuffer> supplier = () -> {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      if (!channel.read(buffer)) {
        return null;
      }
      buffer.flip();
      return buffer;
    };
    unpacker.feed(supplier, channel);
    while (unpacker.hasNext()) {
      try {
        checkPingResultConsumer.accept(pingDecoder.decode(unpacker.next()));
      } catch (final IllegalArgumentException e) {
        logger.error(
            "Received an invalid ping message. remote address = " + channel.getRemoteAddress(), e
        );
      }
    }
  }

  private void receiveRequests() {
    // TODO: optimize
    final Supplier<ByteBuffer> supplier = () -> {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      if (!channel.read(buffer)) {
        return null;
      }
      buffer.flip();
      return buffer;
    };
    unpacker.feed(supplier, channel);
    while (unpacker.hasNext()) {
      try {
        decoder.decode(unpacker.next()).ifPresent(result -> {
          logger.debug(
              "Received a forward request from {}. chunk_id = {}",
              channel.getRemoteAddress(), result.getOption()
          );
          callback.consume(result.getStream()).thenRun(() -> {
            // Executes on user's callback thread since the queue never block.
            result.getOption().getChunk().ifPresent(chunk -> completeTask(chunk));
            logger.debug("Completed the task. chunk_id = {}.", result.getOption());
          });
        });
      } catch (final IllegalArgumentException e) {
        logger.error(
            "Received an invalid message. remote address = " + channel.getRemoteAddress(), e
        );
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

  @Override
  public void close() {
    channel.close();
    logger.debug("NioTcpForwardConnection bound with {} closed.", channel.getRemoteAddress());
  }

  @Override
  public String toString() {
    return "NioTcpForwardConnection(" + channel.getRemoteAddress() + ")";
  }
}
