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
import influent.internal.util.Exceptions;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.*;

/**
 * A adapter of {@code ServerSocketChannel}.
 * The main purpose is to handle complex errors.
 */
final class NioServerSocketChannel {
  private final ServerSocketChannel channel;
  private final SocketAddress localAddress;

  NioServerSocketChannel(final ServerSocketChannel channel, final SocketAddress localAddress) {
    this.channel = channel;
    this.localAddress = localAddress;
  }

  private NioServerSocketChannel(final ServerSocketChannel channel, final NioEventLoop eventLoop, final SocketAddress localAddress, final NioTcpConfig config, final NioAttachment attachment) {
    this.channel = channel;
    this.localAddress = localAddress;

    setOption(channel, StandardSocketOptions.SO_REUSEADDR, true);
    bind(channel, localAddress, config.getBacklog().orElse(0));
    config.getReceiveBufferSize().ifPresent((receiveBufferSize) ->
            setOption(channel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize)
    );
    eventLoop.register(channel, SelectionKey.OP_ACCEPT, attachment);
  }

  private static void bind(final ServerSocketChannel channel, final SocketAddress localAddress,
      final int backlog) {
    try {
      channel.bind(localAddress, backlog);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      throw new IllegalArgumentException("ServerSocketChannel#bind failed", e);
    } catch (final SecurityException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("ServerSocketChannel#bind failed", e);
    }
  }

  private static <T> void setOption(final ServerSocketChannel channel, final SocketOption<T> name,
      final T value) {
    try {
      channel.setOption(name, value);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("ServerSocketChannel#setOption failed", e);
    }
  }

  /**
   * Opens a {@code NioServerSocketChannel}.
   *
   * @param eventLoop    the {@code NioEventLoop}
   * @param localAddress the server's address
   * @return {@code ServerSocketChannel}
   * @throws InfluentIOException      when ServerSocketChannel#open fails
   * @throws IllegalArgumentException when the local address is illegal or already bound
   */
  static NioServerSocketChannel open(final NioEventLoop eventLoop,
      final SocketAddress localAddress, final NioTcpConfig config, final NioAttachment attachment) {
    try {
      return new NioServerSocketChannel(ServerSocketChannel.open(), eventLoop, localAddress,
          config, attachment);
    } catch (final IOException e) {
      throw new InfluentIOException("ServerSocketChannel#open failed", e);
    }
  }


  /**
   * Accepts a new connection.
   *
   * @return the new {@code SocketChannel}
   * @throws InfluentIOException when ServerSocketChannel#accept fails
   *                             typically, this channel is already closed
   */
  SocketChannel accept() {
    try {
      return channel.accept();
    } catch (final NotYetBoundException | SecurityException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("NioTcpAcceptor failed accepting.", e);
    }
  }

  /**
   * Closes this {@code NioServerSocketChannel}.
   */
  void close() {
    Exceptions.ignore(channel::close,
            "The acceptor bound with " + localAddress + " closed.");
  }

  /**
   * Returns server's address.
   * This method does not return null even if this channel is closed.
   */
  SocketAddress getLocalAddress() {
    return localAddress;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NioServerSocketChannel(" + localAddress + ')';
  }
}
