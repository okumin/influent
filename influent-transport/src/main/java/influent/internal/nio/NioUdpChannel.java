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
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;

/**
 * A non-blocking mode {@code DatagramChannel}.
 */
public final class NioUdpChannel extends NioSelectableChannel implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(NioUdpChannel.class);

  private final SocketAddress localAddress;
  private final DatagramChannel channel;

  NioUdpChannel(final DatagramChannel channel) {
    this.channel = channel;
    this.localAddress = Exceptions.orNull(channel::getLocalAddress);
  }

  /**
   * Constructs a new {@code NioUdpChannel}.
   *
   * @param localAddress the local address
   * @param sendBufferSize the size of socket send buffer
   * @param receiveBufferSize the size of socket receive buffer
   * @throws IllegalArgumentException if the local address is invalid or already used
   * @throws InfluentIOException if some IO error occurs
   */
  public NioUdpChannel(final SocketAddress localAddress, final int sendBufferSize,
      final int receiveBufferSize) {
    this.localAddress = localAddress;

    try {
      channel = DatagramChannel.open();
      if (sendBufferSize > 0) {
        channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
      }
      if (receiveBufferSize > 0) {
        channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
      }
      channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      channel.bind(localAddress);
      logger.info("A NioUdpChannel is bound with {}.", localAddress);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      final String message = "The given local address is invalid. address = " + localAddress;
      throw new IllegalArgumentException(message, e);
    } catch (final UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      throw new InfluentIOException("An unexpected IO error occurred.", e);
    }
  }

  /**
   * Sends a datagram.
   * {@code send} sends all or nothing of {@code src}.
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
   * Registers the this channel to the given {@code NioEventLoop}.
   * This method is thread-safe.
   *
   * @param eventLoop the {@code NioEventLoop}
   * @param ops the interest set
   * @param attachment the {@code NioAttachment}
   */
  public void register(final NioEventLoop eventLoop, final int ops, final NioAttachment attachment) {
    eventLoop.register(this, ops, attachment);
  }

  /**
   * Enables OP_WRITE.
   * Operations are done asynchronously.
   *
   * @param eventLoop the {@code NioEventLoop}
   */
  public void enableOpWrite(final NioEventLoop eventLoop) {
    eventLoop.enableInterestSet(selectionKey(), SelectionKey.OP_WRITE);
  }

  /**
   * Disables OP_WRITE.
   * Operations are done asynchronously.
   *
   * @param eventLoop the {@code NioEventLoop}
   */
  public void disableOpWrite(final NioEventLoop eventLoop) {
    eventLoop.disableInterestSet(selectionKey(), SelectionKey.OP_WRITE);
  }

  /**
   * Closes the {@code DatagramChannel}.
   */
  @Override
  public void close() {
    Exceptions.ignore(channel::close,
        "An IO error occurred when closing DatagramChannel. local address = " + getLocalAddress());
  }

  /**
   * @return the local address
   */
  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  SelectableChannel unwrap() {
    return channel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NioUdpChannel(" + getLocalAddress() + ")";
  }
}
