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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import influent.internal.nio.NioChannel;
import influent.internal.nio.NioChannelFactory;
import influent.internal.nio.NioChannelConfig;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import influent.exception.InfluentIOException;
import influent.internal.msgpack.MsgpackStreamUnpacker;
import influent.internal.nio.NioAttachment;
import influent.internal.nio.NioEventLoop;
import influent.internal.util.ThreadSafeQueue;

/**
 * A connection for forward protocol.
 */
final class NioForwardConnection implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioForwardConnection.class);
  private static final String ACK_KEY = "ack";

  private final NioChannel channel;
  private final NioEventLoop eventLoop;
  private final ForwardCallback callback;
  private final MsgpackStreamUnpacker unpacker;
  private final MsgpackForwardRequestDecoder decoder;

  final ThreadSafeQueue<ByteBuffer> responses = new ThreadSafeQueue<>();

  NioForwardConnection(final NioChannel channel,
                       final NioEventLoop eventLoop,
                       final ForwardCallback callback,
                       final MsgpackStreamUnpacker unpacker,
                       final MsgpackForwardRequestDecoder decoder) {
    this.channel = channel;
    this.eventLoop = eventLoop;
    this.callback = callback;
    this.unpacker = unpacker;
    this.decoder = decoder;
  }

  NioForwardConnection(final NioChannel channel,
                       final NioEventLoop eventLoop,
                       final ForwardCallback callback,
                       final long chunkSizeLimit) {
    this(
        channel,
        eventLoop,
        callback,
        new MsgpackStreamUnpacker(chunkSizeLimit),
        new MsgpackForwardRequestDecoder()
    );
  }

  /**
   * Constructs a new {@code NioForwardConnection}.
   *
   * @param socketChannel the inbound channel
   * @param eventLoop the {@code NioEventLoop} to which this {@code NioForwardConnection} belongs
   * @param callback the callback to handle requests
   * @param chunkSizeLimit the allowable size of a chunk
   * @param sendBufferSize enqueue buffer size
   *                       the default value is used when the given {@code value} is empty
   * @param keepAliveEnabled whether SO_KEEPALIVE is enabled or not
   * @param tcpNoDelayEnabled whether TCP_NODELAY is enabled or not
   * @throws InfluentIOException if some IO error occurs
   */
  NioForwardConnection(final SocketChannel socketChannel,
                       final NioEventLoop eventLoop,
                       final ForwardCallback callback,
                       final long chunkSizeLimit,
                       final int sendBufferSize,
                       final boolean keepAliveEnabled,
                       final boolean tcpNoDelayEnabled,
                       final NioChannelConfig sslConfig) {
    this(
        NioChannelFactory.create(socketChannel, sendBufferSize, keepAliveEnabled, tcpNoDelayEnabled, sslConfig),
        eventLoop,
        callback,
        chunkSizeLimit
    );

    channel.register(eventLoop, SelectionKey.OP_READ, this);
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
    receiveRequests(key);
    if (!channel.isOpen()) {
      close();
    }
  }

  private void receiveRequests(final SelectionKey key) {
    unpacker.feed(channel);
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
