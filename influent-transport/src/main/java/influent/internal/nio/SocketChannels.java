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
import java.nio.channels.SocketChannel;
import influent.exception.InfluentIOException;

/**
 * Provides utilities for {@code SocketChannel}.
 */
final class SocketChannels {
  private SocketChannels() {
    throw new AssertionError();
  }

  /**
   * Invokes SocketChannel#setOption and handles an error.
   * This method expects that {@code name} and {@value} is valid.
   *
   * @param channel the {@code SocketChannel}
   * @param name the option name
   * @param value the option value
   * @param <T> the type of option
   * @return {@code SocketChannel}
   */
  static <T> SocketChannel setOption(final SocketChannel channel, final SocketOption<T> name,
      final T value) {
    try {
      return channel.setOption(name, value);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("SocketChannel#setOption failed", e);
    }
  }

  /**
   * Invokes SocketChannel#getRemoteAddress and handles an error.
   *
   * @param channel the {@code SocketChannel}
   * @return {@code SocketAddress}
   */
  static SocketAddress getRemoteAddress(final SocketChannel channel) {
    try {
      return channel.getRemoteAddress();
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("SocketChannel#getRemoteAddress failed", e);
    }
  }
}
