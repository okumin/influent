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
import java.net.SocketOption;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;

import influent.exception.InfluentIOException;

/**
 * Provides utilities for {@code ServerSocketChannel}.
 */
final class ServerSocketChannels {
  private ServerSocketChannels() {
    throw new AssertionError();
  }

  /**
   * Invokes ServerSocketChannel#open and handles an error.
   *
   * @return {@code ServerSocketChannel}
   */
  static ServerSocketChannel open() {
    try {
      return ServerSocketChannel.open();
    } catch (final IOException e) {
      throw new InfluentIOException("ServerSocketChannel#open failed", e);
    }
  }

  /**
   * Invokes ServerSocketChannel#bind and handles an error.
   *
   * @param channel the {@code ServerSocketChannel}
   * @param localAddress the server's address
   * @param backlog the backlog
   * @return {@code ServerSocketChannel}
   */
  static ServerSocketChannel bind(final ServerSocketChannel channel,
      final SocketAddress localAddress, final int backlog) {
    try {
      return channel.bind(localAddress, backlog);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      throw new IllegalArgumentException("ServerSocketChannel#bind failed", e);
    } catch (final SecurityException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("ServerSocketChannel#bind failed", e);
    }
  }

  /**
   * Invokes ServerSocketChannel#setOption and handles an error.
   * This method expects that {@code name} and {@value} is valid.
   *
   * @param channel the {@code ServerSocketChannel}
   * @param name the option name
   * @param value the option value
   * @param <T> the type of option
   * @return {@code ServerSocketChannel}
   */
  static <T> ServerSocketChannel setOption(final ServerSocketChannel channel,
      final SocketOption<T> name, final T value) {
    try {
      return channel.setOption(name, value);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("ServerSocketChannel#setOption failed", e);
    }
  }
}
