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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A non-blocking {@code DatagramChannel}. */
public final class NioUdpChannel implements AutoCloseable {
  public enum Op {
    /** OP_READ */
    READ(SelectionKey.OP_READ),
    /** OP_WRITE */
    WRITE(SelectionKey.OP_WRITE);

    private final int bit;

    Op(final int bit) {
      this.bit = bit;
    }

    int getBit() {
      return bit;
    }

    static int bits(final Set<Op> ops) {
      return ops.stream().mapToInt(Op::getBit).reduce(0, (x, y) -> x | y);
    }
  }

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
   * Registers the this channel to the given {@code NioEventLoop}.
   *
   * <p>This method is thread-safe.
   *
   * @param ops the operations to be enabled
   * @param attachment the {@code NioAttachment}
   */
  public void register(final Set<Op> ops, final NioAttachment attachment) {
    eventLoop.register(channel, key, Op.bits(ops), attachment);
  }

  /**
   * Enables the given operation.
   *
   * <p>Operations are done asynchronously.
   *
   * @param op the operation to be enabled
   */
  public void enable(final Op op) {
    eventLoop.enableInterestSet(key, op.getBit());
  }

  /**
   * Disables the given operation.
   *
   * <p>Operations are done asynchronously.
   *
   * @param op the operation to be enabled
   */
  public void disable(final Op op) {
    eventLoop.disableInterestSet(key, op.getBit());
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
