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

package influent.internal.nio;

import influent.exception.InfluentIOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TCP acceptor.
 *
 * <p>{@code NioTcpAcceptor} is not thread-safe and expected to be executed on the event loop
 * thread.
 */
public final class NioTcpAcceptor implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioTcpAcceptor.class);

  private final NioServerSocketChannel serverSocketChannel;
  private final Consumer<SocketChannel> callback;

  NioTcpAcceptor(
      final NioServerSocketChannel serverSocketChannel, final Consumer<SocketChannel> callback) {
    this.serverSocketChannel = serverSocketChannel;
    this.callback = callback;
  }

  /**
   * Creates a new {@code NioTcpAcceptor}.
   *
   * @param localAddress the local address to bind
   * @param eventLoop the {@code NioEventLoop}
   * @param callback the callback function which is invoked on acceptances
   * @param tcpConfig the {@code NioTcpConfig}
   * @throws IllegalArgumentException if the given local address is invalid or already used
   * @throws InfluentIOException if some IO error occurs
   */
  public static NioTcpAcceptor open(
      final SocketAddress localAddress,
      final NioEventLoop eventLoop,
      final Consumer<SocketChannel> callback,
      final NioTcpConfig tcpConfig) {
    final NioServerSocketChannel serverSocketChannel =
        NioServerSocketChannel.open(localAddress, tcpConfig);
    final NioTcpAcceptor acceptor = new NioTcpAcceptor(serverSocketChannel, callback);
    serverSocketChannel.register(eventLoop, acceptor);
    logger.info("A NioTcpAcceptor is bound with {}.", localAddress);
    return acceptor;
  }

  /** Handles an accept event. This method never fails. */
  @Override
  public void onAcceptable() {
    while (true) {
      try {
        final SocketChannel channel = serverSocketChannel.accept();
        if (channel == null) {
          break;
        }
        callback.accept(channel);
      } catch (final Exception e) {
        logger.error("NioTcpAcceptor failed accepting.", e);
      }
    }
  }

  /** Closes this {@code NioTcpAcceptor}. */
  @Override
  public void close() {
    serverSocketChannel.close();
    logger.info("The acceptor bound with {} closed.", serverSocketChannel.getLocalAddress());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "NioTcpAcceptor(" + serverSocketChannel.getLocalAddress() + ")";
  }
}
