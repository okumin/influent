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

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;

/**
 * A TCP acceptor.
 *
 * {@code NioTcpAcceptor} is not thread-safe and expected to be executed on the event loop thread.
 */
public final class NioTcpAcceptor implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioTcpAcceptor.class);

  private final SocketAddress localAddress;
  private final Consumer<SocketChannel> callback;
  private final ServerSocketChannel serverSocketChannel;

  NioTcpAcceptor(final SocketAddress localAddress, final Consumer<SocketChannel> callback,
      final ServerSocketChannel serverSocketChannel) {
    this.localAddress = localAddress;
    this.callback = callback;
    this.serverSocketChannel = serverSocketChannel;
  }

  /**
   * Constructs a new {@code NioTcpAcceptor}.
   *
   * @param localAddress the local address to bind
   * @param eventLoop the {@code NioEventLoop}
   * @param callback the callback function which is invoked on acceptances
   * @param backlog the maximum number of pending connections
   * @param receiveBufferSize the socket receive buffer size
   * @throws IllegalArgumentException if the given local address is invalid or already used
   * @throws InfluentIOException if some IO error occurs
   */
  public NioTcpAcceptor(final SocketAddress localAddress, final NioEventLoop eventLoop,
      final Consumer<SocketChannel> callback, final int backlog, final int receiveBufferSize) {
    this.localAddress = localAddress;
    this.callback = callback;
    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      serverSocketChannel.bind(this.localAddress, backlog);
      if (receiveBufferSize > 0) {
        serverSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
      }
      eventLoop.register(serverSocketChannel, SelectionKey.OP_ACCEPT, this);
      logger.info("A NioTcpAcceptor is bound with {}.", localAddress);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      throw new IllegalArgumentException(e);
    } catch (final ClosedChannelException | SecurityException | UnsupportedOperationException
        | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      final String message = "An IO error occurred. local address = " + this.localAddress;
      throw new InfluentIOException(message, e);
    }
  }

  /**
   * Handles an accept event.
   * This method never fails.
   *
   * @param key the {@code SelectionKey}
   */
  @Override
  public void onAcceptable(final SelectionKey key) {
    while (true) {
      try {
        final SocketChannel channel = accept();
        if (channel == null) {
          break;
        }
        callback.accept(channel);
      } catch (final Exception e) {
        logger.error("NioTcpAcceptor failed accepting.", e);
      }
    }
  }

  private SocketChannel accept() {
    try {
      return serverSocketChannel.accept();
    } catch (final NotYetBoundException | SecurityException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("NioTcpAcceptor failed accepting.", e);
    }
  }

  /**
   * Closes this {@code NioTcpAcceptor}.
   */
  @Override
  public void close() {
    Exceptions.ignore(serverSocketChannel::close,
        "The acceptor bound with " + localAddress + " closed.");
    logger.info("The acceptor bound with {} closed.", localAddress);
  }

  @Override
  public String toString() {
    return "NioTcpAcceptor(" + localAddress + ")";
  }
}
