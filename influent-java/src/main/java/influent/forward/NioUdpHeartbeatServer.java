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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import influent.internal.nio.NioAttachment;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioUdpChannel;
import influent.internal.util.ThreadSafeQueue;

/**
 * A heartbeat server for forward protocol.
 *
 * {@code NioUdpHeartbeatServer} is not thread-safe and expected to be executed on the event loop thread.
 */
final class NioUdpHeartbeatServer implements NioAttachment {
  private static final byte RESPONSE_BYTE = 0;
  private static final int SOCKET_BUFFER_SIZE = 8;
  private static final Logger logger = LoggerFactory.getLogger(NioUdpHeartbeatServer.class);

  private final ByteBuffer response = ByteBuffer.allocate(Byte.BYTES).put(RESPONSE_BYTE);
  final ThreadSafeQueue<SocketAddress> replyTo = new ThreadSafeQueue<>();
  private final ByteBuffer receiveBuffer = ByteBuffer.allocate(1);

  private final NioEventLoop eventLoop;
  private final NioUdpChannel channel;

  NioUdpHeartbeatServer(final NioUdpChannel channel, final NioEventLoop eventLoop) {
    this.eventLoop = eventLoop;
    this.channel = channel;
  }

  /**
   * Constructs a new {@code NioUdpHeartbeatServer}.
   *
   * @param localAddress the local address
   * @param eventLoop the {@code NioEventLoop}
   * @throws IllegalArgumentException if the local address is invalid or already used
   * @throws influent.exception.InfluentIOException if some IO error occurs
   */
  NioUdpHeartbeatServer(final SocketAddress localAddress, final NioEventLoop eventLoop) {
    this(new NioUdpChannel(localAddress, SOCKET_BUFFER_SIZE, SOCKET_BUFFER_SIZE), eventLoop);
    channel.register(eventLoop, SelectionKey.OP_READ, this);
  }

  /**
   * Sends heartbeat responses.
   * {@code NioUdpHeartbeatServer#onWritable} never fails.
   *
   * @param key the {@code SelectionKey}
   */
  @Override
  public void onWritable(final SelectionKey key) {
    if (sendResponses()) {
      eventLoop.disableInterestSet(key, SelectionKey.OP_WRITE);
    }
  }

  private boolean sendResponses() {
    while (replyTo.nonEmpty()) {
      try {
        final SocketAddress target = replyTo.peek();
        response.rewind();
        if (channel.send(response, target)) {
          replyTo.dequeue();
        } else {
          return false; // unsent responses are remaining
        }
      } catch (final Exception e) {
        logger.error("Failed sending a response.", e);
      }
    }
    return true;
  }

  /**
   * Receives heartbeat requests.
   * {@code NioUdpHeartbeatServer#onReadable} never fails.
   *
   * @param key the {@code SelectionKey}
   */
  @Override
  public void onReadable(final SelectionKey key) {
    while (true) {
      receiveBuffer.rewind();
      try {
        receiveBuffer.rewind();
        final Optional<SocketAddress> source = channel.receive(receiveBuffer);
        if (source.isPresent()) {
          logger.debug("Received a heartbeat request from {}.", source);
          replyTo.enqueue(source.get());
        } else {
          break;
        }
      } catch (final Exception e) {
        logger.error("Failed receiving a request.", e);
      }
    }

    eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    channel.close();
    logger.info("The heartbeat server bound with {} closed.", channel.getLocalAddress());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NioUdpHeartbeatServer(" + channel.getLocalAddress() + ")";
  }
}
