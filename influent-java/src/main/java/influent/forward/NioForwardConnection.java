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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import influent.exception.InfluentIOException;
import influent.internal.msgpack.MsgpackStreamUnpacker;
import influent.internal.nio.NioAttachment;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioTcpChannel;
import influent.internal.nio.NioTcpConfig;
import influent.internal.util.ThreadSafeQueue;

/**
 * A connection for forward protocol.
 */
final class NioForwardConnection implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioForwardConnection.class);
  private static final String ACK_KEY = "ack";

  private final NioTcpChannel channel;
  private final NioEventLoop eventLoop;
  private final ForwardCallback callback;
  private final MsgpackStreamUnpacker unpacker;
  private final MsgpackForwardRequestDecoder decoder;
  private final ForwardSecurity security;
  private MsgPackPingDecoder pingDecoder;
  private Optional<ForwardClientNode> node;

  final ThreadSafeQueue<ByteBuffer> responses = new ThreadSafeQueue<>();

  private final byte[] nonce = new byte[16];
  private final byte[] userAuth = new byte[16];

  enum ConnectionState {
    HELO, PINGPONG, ESTABLISHED
  }

  private ConnectionState state;

  NioForwardConnection(final NioTcpChannel channel, final NioEventLoop eventLoop,
      final ForwardCallback callback, final MsgpackStreamUnpacker unpacker,
      final MsgpackForwardRequestDecoder decoder, final ForwardSecurity security) {
    this.channel = channel;
    this.eventLoop = eventLoop;
    this.callback = callback;
    this.unpacker = unpacker;
    this.decoder = decoder;
    this.security = security;
    state = ConnectionState.ESTABLISHED;
  }

  NioForwardConnection(final NioTcpChannel channel, final NioEventLoop eventLoop,
      final ForwardCallback callback, final long chunkSizeLimit, final ForwardSecurity security) {
    this(channel, eventLoop, callback, new MsgpackStreamUnpacker(chunkSizeLimit),
        new MsgpackForwardRequestDecoder(), security);
  }

  /**
   * Constructs a new {@code NioForwardConnection}.
   *
   * @param socketChannel the inbound channel
   * @param eventLoop the {@code NioEventLoop} to which this {@code NioForwardConnection} belongs
   * @param callback the callback to handle requests
   * @param chunkSizeLimit the allowable size of a chunk
   * @param tcpConfig the {@code NioTcpConfig}
   * @throws InfluentIOException if some IO error occurs
   */
  NioForwardConnection(final SocketChannel socketChannel, final NioEventLoop eventLoop,
      final ForwardCallback callback, final long chunkSizeLimit, final NioTcpConfig tcpConfig,
      final ForwardSecurity security) {
    this(new NioTcpChannel(socketChannel, tcpConfig), eventLoop, callback, chunkSizeLimit, security);

    if (this.security.isEnabled()) {
      try {
        // SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        // Above secureRandom may block...
        // TODO: reuse SecureRandom instance
        SecureRandom secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking");
        logger.debug(secureRandom.getAlgorithm());
        secureRandom.nextBytes(nonce);
        secureRandom.nextBytes(userAuth);
      } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
      }
      node = security.findNode(((InetSocketAddress) channel.getRemoteAddress()).getAddress());
      state = ConnectionState.HELO;
      pingDecoder = new MsgPackPingDecoder(this.security, node.orElse(null), nonce, userAuth);
      channel.register(eventLoop, SelectionKey.OP_WRITE, this);
      responses.enqueue(generateHelo());
    } else {
      state = ConnectionState.ESTABLISHED;
      channel.register(eventLoop, SelectionKey.OP_READ, this);
    }
  }

  /**
   * Handles a write event.
   *
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onWritable(final SelectionKey key) {
    if (sendResponses()) {
      eventLoop.disableInterestSet(key, SelectionKey.OP_WRITE);
      if (state == ConnectionState.HELO) {
        state = ConnectionState.PINGPONG;
        eventLoop.enableInterestSet(key, SelectionKey.OP_READ);
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
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException if some IO error occurs
   */
  @Override
  public void onReadable(final SelectionKey key) {
    switch (state) {
      case PINGPONG:
        receivePing(key, result -> {
          responses.enqueue(generatePong(result));
          eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
          state = ConnectionState.ESTABLISHED;
        });
        break;
      case ESTABLISHED:
        receiveRequests(key);
        break;
    }
    if (!channel.isOpen()) {
      close();
    }
  }

  private void receivePing(final SelectionKey key, Consumer<CheckPingResult> checkPingResultConsumer) {
    // TODO: optimize
    final Supplier<ByteBuffer> supplier = () -> {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      final int bytes = channel.read(buffer);
      if (bytes <= 0) {
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

  private void receiveRequests(final SelectionKey key) {
    // TODO: optimize
    final Supplier<ByteBuffer> supplier = () -> {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      final int bytes = channel.read(buffer);
      if (bytes <= 0) {
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
            result.getOption().getChunk().ifPresent(chunk -> completeTask(key, chunk));
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
  private void completeTask(final SelectionKey key, final String chunk) {
    try {
      final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
      packer.packMapHeader(1);
      packer.packString(ACK_KEY);
      packer.packString(chunk);
      final ByteBuffer buffer = packer.toMessageBuffer().sliceAsByteBuffer();
      responses.enqueue(buffer);
      eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
    } catch (final IOException e) {
      logger.error("Failed packing. chunk = " + chunk, e);
    }
  }

  // TODO Set keepalive on HELO message true/false according to ForwardServer configuration
  //      ForwardServer.keepAliveEnabled set SO_KEEPALIVE.
  //      See also https://github.com/okumin/influent/pull/32#discussion_r145196969
  private ByteBuffer generateHelo() {
    // ['HELO', options(hash)]
    // ['HELO', {'nonce' => nonce, 'auth' => user_auth_salt/empty string, 'keepalive' => true/false}].to_msgpack
    MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      packer.packArrayHeader(2).packString("HELO").packMapHeader(3).packString("nonce")
          .packBinaryHeader(16).writePayload(nonce).packString("auth").packBinaryHeader(16)
          .writePayload(userAuth).packString("keepalive").packBoolean(true);
    } catch (IOException e) {
      logger.error("Failed to pack HELO message", e);
    }

    return packer.toMessageBuffer().sliceAsByteBuffer();
  }

  private ByteBuffer generatePong(CheckPingResult checkPingResult) {
    // [
    //   'PONG',
    //   bool(authentication result),
    //   'reason if authentication failed',
    //   self_hostname,
    //   sha512_hex(salt + self_hostname + nonce + sharedkey)
    // ]
    MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      if (checkPingResult.isSucceeded()) {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(checkPingResult.getSharedKeySalt().getBytes());
        md.update(security.getSelfHostname().getBytes());
        md.update(nonce);
        md.update(checkPingResult.getSharedKey().getBytes());
        packer.packArrayHeader(5).packString("PONG").packBoolean(checkPingResult.isSucceeded())
            .packString("").packString(security.getSelfHostname())
            .packString(generateHexString(md.digest()));
      } else {
        packer.packArrayHeader(5).packString("PONG").packBoolean(checkPingResult.isSucceeded())
            .packString(checkPingResult.getReason()).packString("").packString("");
      }
    } catch (IOException e) {
      logger.error("Failed to pack PONG message", e);
    } catch (NoSuchAlgorithmException e) {
      logger.error(e.getMessage(), e);
    }

    return packer.toMessageBuffer().sliceAsByteBuffer();
  }

  private String generateHexString(final byte[] digest) {
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Override
  public void close() {
    channel.close();
    logger.debug("NioForwardConnection bound with {} closed.", channel.getRemoteAddress());
  }

  @Override
  public String toString() {
    return "NioForwardConnection(" + channel.getRemoteAddress() + ")";
  }
}
