package influent.internal.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;

/**
 * A non-blocking mode {@code SocketChannel}.
 */
public final class NioTcpChannel implements AutoCloseable {
  private final SocketChannel channel;
  private final SocketAddress remoteAddress;

  NioTcpChannel(final SocketChannel channel) {
    this.channel = channel;
    this.remoteAddress = Exceptions.orNull(channel::getRemoteAddress);
  }

  /**
   * Constructs a new {@code NioTcpChannel}.
   *
   * @param channel the accepted {@code SocketChannel}
   * @param sendBufferSize the socket send buffer size
   * @param keepAliveEnabled whether SO_KEEPALIVE is enabled or not
   * @param tcpNoDelayEnabled whether TCP_NODELAY is enabled or not
   * @throws InfluentIOException if some IO error occurs
   */
  public NioTcpChannel(final SocketChannel channel,
                       final int sendBufferSize,
                       final boolean keepAliveEnabled,
                       final boolean tcpNoDelayEnabled) {
    this.channel = channel;
    this.remoteAddress = Exceptions.orNull(channel::getRemoteAddress);

    try {
      if (sendBufferSize > 0) {
        channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
      }
      channel.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAliveEnabled);
      channel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelayEnabled);
    } catch (final UnsupportedOperationException | IllegalArgumentException e) {
      closeChannel(channel);
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      closeChannel(channel);
      throw new InfluentIOException("An unexpected IO error occurred.", e);
    }
  }

  /**
   * Writes bytes to the socket buffer.
   *
   * @param src the buffer
   * @return the written size
   * @throws InfluentIOException if some IO error occurs
   */
  public int write(final ByteBuffer src) {
    try {
      return channel.write(src);
    } catch (final NotYetConnectedException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final IOException e) {
      // ClosedChannelException is an IOException
      final String message = "This channel is broken. remote address = " + getRemoteAddress();
      throw new InfluentIOException(message, e);
    }
  }

  /**
   * Reads bytes from the socket buffer.
   *
   * @param dst the buffer
   * @return the read size -1 when the stream completes
   * @throws InfluentIOException if some IO error occurs
   */
  public int read(final ByteBuffer dst) {
    try {
      return channel.read(dst);
    } catch (final NotYetConnectedException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final ClosedChannelException e) {
      return -1;
    } catch (final IOException e) {
      final String message = "This channel is broken. remote address = " + getRemoteAddress();
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
  public void register(final NioEventLoop eventLoop,
                       final int ops,
                       final NioAttachment attachment) {
    eventLoop.register(channel, ops, attachment);
  }

  /**
   * Closes the {@code SocketChannel}.
   */
  @Override
  public void close() {
    closeChannel(channel);
  }

  private void closeChannel(final SocketChannel channel) {
    Exceptions.ignore(channel::close, "Failed closing the socket channel." + getRemoteAddress());
  }

  /**
   * @return the remote address
   */
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NioTcpChannel(" + getRemoteAddress() + ")";
  }
}
