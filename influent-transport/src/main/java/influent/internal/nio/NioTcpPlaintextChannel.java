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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Set;

/** A non-blocking {@code SocketChannel}. */
public final class NioTcpPlaintextChannel implements NioTcpChannel {
  private final SocketChannel channel;
  private final NioEventLoop eventLoop;
  private final SocketAddress remoteAddress;

  final NioSelectionKey key = NioSelectionKey.create();

  NioTcpPlaintextChannel(
      final SocketChannel channel,
      final NioEventLoop eventLoop,
      final SocketAddress remoteAddress) {
    this.channel = channel;
    this.eventLoop = eventLoop;
    this.remoteAddress = remoteAddress;
  }

  private static SocketAddress getRemoteAddress(final SocketChannel channel) {
    try {
      return channel.getRemoteAddress();
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("SocketChannel#getRemoteAddress failed", e);
    }
  }

  private static <T> void setOption(
      final SocketChannel channel, final SocketOption<T> name, final T value) {
    try {
      channel.setOption(name, value);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("SocketChannel#setOption failed", e);
    }
  }

  /**
   * Creates a new {@code NioTcpPlaintextChannel}.
   *
   * @param channel the accepted {@code SocketChannel}
   * @param eventLoop the {@code NioEventLoop}
   * @param tcpConfig the {@code NioTcpConfig}
   * @throws InfluentIOException if some IO error occurs
   */
  public static NioTcpPlaintextChannel open(
      final SocketChannel channel, final NioEventLoop eventLoop, final NioTcpConfig tcpConfig) {
    try {
      final SocketAddress remoteAddress = getRemoteAddress(channel);
      tcpConfig
          .getSendBufferSize()
          .ifPresent(
              (sendBufferSize) ->
                  setOption(channel, StandardSocketOptions.SO_SNDBUF, sendBufferSize));
      setOption(channel, StandardSocketOptions.SO_KEEPALIVE, tcpConfig.getKeepAliveEnabled());
      setOption(channel, StandardSocketOptions.TCP_NODELAY, tcpConfig.getTcpNoDelayEnabled());
      return new NioTcpPlaintextChannel(channel, eventLoop, remoteAddress);
    } catch (final Exception e) {
      closeChannel(channel, Exceptions.orNull(channel::getRemoteAddress));
      throw e;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean write(final ByteBuffer src) {
    try {
      return channel.write(src) > 0;
    } catch (final NotYetConnectedException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      close();
      final String message = "This channel is broken. remote address = " + getRemoteAddress();
      throw new InfluentIOException(message, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean read(final ByteBuffer dst) {
    try {
      final int readSize = channel.read(dst);
      if (readSize == -1) {
        close();
      }
      return readSize > 0;
    } catch (final NotYetConnectedException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final ClosedChannelException e) {
      close();
      return false;
    } catch (final IOException e) {
      close();
      final String message = "This channel is broken. remote address = " + getRemoteAddress();
      throw new InfluentIOException(message, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void register(final Set<Op> ops, final NioAttachment attachment) {
    eventLoop.register(channel, key, Op.bits(ops), attachment);
  }

  /** {@inheritDoc} */
  @Override
  public void enable(final Op op) {
    eventLoop.enableInterestSet(key, op.getBit());
  }

  /** {@inheritDoc} */
  @Override
  public void disable(final Op op) {
    eventLoop.disableInterestSet(key, op.getBit());
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    closeChannel(channel, getRemoteAddress());
  }

  private static void closeChannel(final SocketChannel channel, final SocketAddress remoteAddress) {
    Exceptions.ignore(channel::close, "Failed closing the socket channel." + remoteAddress);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOpen() {
    return Exceptions.orFalse(channel::isOpen);
  }

  /** {@inheritDoc} */
  @Override
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "NioTcpPlaintextChannel(" + getRemoteAddress() + ")";
  }
}
