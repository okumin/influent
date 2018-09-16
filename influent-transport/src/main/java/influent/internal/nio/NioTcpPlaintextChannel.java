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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

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

  /**
   * Writes bytes to the socket buffer.
   *
   * @param src the buffer
   * @return true when some bytes are written
   * @throws InfluentIOException if some IO error occurs
   */
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

  /**
   * Reads bytes from the socket buffer.
   *
   * @param dst the buffer
   * @return true when some bytes are read
   * @throws InfluentIOException if some IO error occurs
   */
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

  /**
   * Registers the this channel to the given {@code NioEventLoop}. This method is thread-safe.
   *
   * @param opReadEnabled whether OP_READ is enabled or not
   * @param opWriteEnabled whether OP_WRITE is enabled or not
   * @param attachment the {@code NioAttachment}
   */
  @Override
  public void register(
      final boolean opReadEnabled, final boolean opWriteEnabled, final NioAttachment attachment) {
    int ops = 0;
    if (opReadEnabled) {
      ops |= SelectionKey.OP_READ;
    }
    if (opWriteEnabled) {
      ops |= SelectionKey.OP_WRITE;
    }
    eventLoop.register(channel, key, ops, attachment);
  }

  /** Enables OP_READ. Operations are done asynchronously. */
  @Override
  public void enableOpRead() {
    eventLoop.enableInterestSet(key, SelectionKey.OP_READ);
  }

  /** Enables OP_WRITE. Operations are done asynchronously. */
  @Override
  public void enableOpWrite() {
    eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
  }

  /** Disables OP_WRITE. Operations are done asynchronously. */
  @Override
  public void disableOpWrite() {
    eventLoop.disableInterestSet(key, SelectionKey.OP_WRITE);
  }

  /** Closes the {@code SocketChannel}. */
  @Override
  public void close() {
    closeChannel(channel, getRemoteAddress());
  }

  private static void closeChannel(final SocketChannel channel, final SocketAddress remoteAddress) {
    Exceptions.ignore(channel::close, "Failed closing the socket channel." + remoteAddress);
  }

  /** @return true if this channel is open */
  @Override
  public boolean isOpen() {
    return Exceptions.orFalse(channel::isOpen);
  }

  /** @return the remote address */
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
