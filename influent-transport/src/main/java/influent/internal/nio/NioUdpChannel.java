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
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A non-blocking {@code DatagramChannel}. */
public final class NioUdpChannel implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(NioUdpChannel.class);

  private final DatagramChannel channel;
  private final NioEventLoop eventLoop;
  private final SocketAddress localAddress;

  final NioSelectionKey key = NioSelectionKey.create();

  NioUdpChannel(
      final DatagramChannel channel, final NioEventLoop eventLoop, SocketAddress localAddress) {
    this.channel = channel;
    this.eventLoop = eventLoop;
    this.localAddress = localAddress;
  }

  private static DatagramChannel newChannel() {
    try {
      return DatagramChannel.open();
    } catch (final IOException e) {
      throw new InfluentIOException("DatagramChannel#open failed", e);
    }
  }

  private static void bind(final DatagramChannel channel, final SocketAddress localAddress) {
    try {
      channel.bind(localAddress);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      throw new IllegalArgumentException("DatagramChannel#bind failed", e);
    } catch (final SecurityException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("DatagramChannel#bind failed", e);
    }
  }

  private static <T> void setOption(
      final DatagramChannel channel, final SocketOption<T> name, final T value) {
    try {
      channel.setOption(name, value);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("DatagramChannel#setOption failed", e);
    }
  }

  /**
   * Creates a new {@code NioUdpChannel}.
   *
   * @param localAddress the local address
   * @param eventLoop the {@code NioEventLoop}
   * @param sendBufferSize the size of socket send buffer
   * @param receiveBufferSize the size of socket receive buffer
   * @throws IllegalArgumentException if the local address is invalid or already used
   * @throws InfluentIOException if some IO error occurs
   */
  public static NioUdpChannel open(
      final NioEventLoop eventLoop,
      final SocketAddress localAddress,
      final int sendBufferSize,
      final int receiveBufferSize) {
    final DatagramChannel channel = newChannel();
    if (sendBufferSize > 0) {
      setOption(channel, StandardSocketOptions.SO_SNDBUF, sendBufferSize);
    }
    if (receiveBufferSize > 0) {
      setOption(channel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
    }
    setOption(channel, StandardSocketOptions.SO_REUSEADDR, true);
    bind(channel, localAddress);
    final NioUdpChannel udpChannel = new NioUdpChannel(channel, eventLoop, localAddress);
    logger.info("A NioUdpChannel is bound with {}.", localAddress);
    return udpChannel;
  }

  /**
   * Sends a datagram. {@code send} sends all or nothing of {@code src}.
   *
   * @param src the buffer to send
   * @param target the target address
   * @return whether the given buffer is sent or not
   * @throws InfluentIOException when some IO error occurs
   */
  public boolean send(final ByteBuffer src, final SocketAddress target) {
    try {
      return channel.send(src, target) != 0;
    } catch (final SecurityException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError();
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      close();
      final String message = "The channel is broken. local address = " + getLocalAddress();
      throw new InfluentIOException(message, e);
    }
  }

  /**
   * Receives a datagram.
   *
   * @param dst the buffer to receive
   * @return the source address or {@code Optional.empty()} when no datagram is available
   * @throws InfluentIOException when some IO error occurs
   */
  public Optional<SocketAddress> receive(final ByteBuffer dst) {
    try {
      return Optional.ofNullable(channel.receive(dst));
    } catch (final SecurityException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      close();
      final String message = "The channel is broken. local address = " + getLocalAddress();
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

  /**
   * Enables OP_WRITE.
   *
   * <p>Operations are done asynchronously.
   */
  public void enableOpWrite() {
    eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
  }

  /**
   * Disables OP_WRITE.
   *
   * <p>Operations are done asynchronously.
   */
  public void disableOpWrite() {
    eventLoop.disableInterestSet(key, SelectionKey.OP_WRITE);
  }

  /** Closes the {@code DatagramChannel}. */
  @Override
  public void close() {
    Exceptions.ignore(
        channel::close,
        "An IO error occurred when closing DatagramChannel. local address = " + getLocalAddress());
  }

  /** @return the local address */
  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "NioUdpChannel(" + getLocalAddress() + ")";
  }
}
