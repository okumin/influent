package influent.internal.nio;

import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

public class NioSslChannel implements NioChannel {
  private static final Logger logger = LoggerFactory.getLogger(NioSslChannel.class);

  private final SocketChannel channel;
  private final SocketAddress remoteAddress;
  private final NioChannelConfig sslConfig;
  private final SSLEngine engine;

  public NioSslChannel(final SocketChannel channel) {
      this.channel = channel;
      this.remoteAddress = Exceptions.orNull(channel::getRemoteAddress);
      this.sslConfig = new NioChannelConfig();
      engine = null;
  }

  public NioSslChannel(final SocketChannel channel,
                       final int sendBufferSize,
                       final boolean keepAliveEnabled,
                       final boolean tcpNoDelayEnabled,
                       final NioChannelConfig sslConfig) {
      this.channel = channel;
      this.remoteAddress = Exceptions.orNull(channel::getRemoteAddress);
      this.sslConfig = sslConfig;
      engine = sslConfig.createSSLEngine();

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
      ByteBuffer wrapped = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
      int writeSize = 0;
      while (src.hasRemaining()) {
        wrapped.clear();
        SSLEngineResult result = engine.wrap(src, wrapped);
        switch (result.getStatus()) {
          case OK:
            wrapped.flip();
            while (wrapped.hasRemaining()) {
              writeSize += channel.write(wrapped);
            }
            break;
          case BUFFER_OVERFLOW:
            wrapped = ByteBuffer.allocate(wrapped.capacity() * 2);
            break;
          case BUFFER_UNDERFLOW:
            throw new SSLException("Buffer overflow. Must not happen.");
          case CLOSED:
            close();
            return writeSize;
          default:
            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
        }
      }
      return writeSize;
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
   * @return the read size -1 when the stream completes
   * @throws InfluentIOException if some IO error occurs
   */
  public int read(final ByteBuffer dst) {
    logger.debug("Start reading...");
    try {
      ByteBuffer wrapped = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
      final int readSize = channel.read(wrapped);
      if (readSize < 0) {
        close();
        return readSize;
      }
      wrapped.flip();
      while (wrapped.hasRemaining()) {
        SSLEngineResult result = engine.unwrap(wrapped, dst);
        switch (result.getStatus()) {
          case OK:
            logger.debug("OK");
            break;
          case BUFFER_OVERFLOW:
            logger.debug("buffer overflow");
            break;
          case BUFFER_UNDERFLOW:
            logger.debug("buffer underflow");
            break;
          case CLOSED:
            logger.debug("Client closes connection");
            close();
            break;
          default:
            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
        }
      }
      return readSize;
    } catch (final NotYetConnectedException | AsynchronousCloseException e) {
      // ClosedByInterruptException is an AsynchronousCloseException
      throw new AssertionError(e);
    } catch (final ClosedChannelException e) {
      close();
      return -1;
    } catch (final IOException e) {
      close();
      final String message = "This channel is broken. remote address = " + getRemoteAddress();
      throw new InfluentIOException(message, e);
    }
  }

  public void register(final NioEventLoop eventLoop,
                       final int ops,
                       final NioAttachment attachment) {
    eventLoop.register(channel, ops, attachment);
  }

  private void closeChannel(SocketChannel channel) {
    Exceptions.ignore(channel::close, "Failed closing the socket channel." + getRemoteAddress());
  }

  @Override
  public void close() {
    closeChannel(channel);
  }

  public boolean isOpen() {
    return Exceptions.orFalse(channel::isOpen);
  }

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
