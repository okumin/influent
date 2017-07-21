package influent.internal.nio;

import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class NioSslAcceptor implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioSslAcceptor.class);
  private final SocketAddress localAddress;
  private final Consumer<SocketChannel> callback;
  private final ServerSocketChannel serverSocketChannel;
  private final SSLContext context;

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  /**
   * @param localAddress
   * @param callback
   * @param serverSocketChannel
   */
  NioSslAcceptor(final InetSocketAddress localAddress,
                 final Consumer<SocketChannel> callback,
                 final ServerSocketChannel serverSocketChannel,
                 final SSLContext context) {
    this.localAddress = localAddress;
    this.callback = callback;
    this.serverSocketChannel = serverSocketChannel;
    this.context = context;
  }

  public NioSslAcceptor(final SocketAddress localAddress,
                        final NioEventLoop eventLoop,
                        final Consumer<SocketChannel> callback,
                        final int backlog,
                        final int receiveBufferSize,
                        final SSLContext context) {
    this.localAddress = localAddress;
    this.callback = callback;
    this.context = context;
    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      serverSocketChannel.bind(this.localAddress, backlog);
      if (receiveBufferSize > 0) {
        serverSocketChannel
            .setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
      }
      eventLoop.register(serverSocketChannel, SelectionKey.OP_ACCEPT, this);
      logger.info("A NioSslAcceptor is bound with {}.", localAddress);
    } catch (final AlreadyBoundException | UnsupportedAddressTypeException e) {
      throw new IllegalArgumentException(e);
    } catch (final ClosedChannelException
        | SecurityException
        | UnsupportedOperationException
        | IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (final IOException e) {
      final String message = "An IO error occurred. local address = " + this.localAddress;
      throw new InfluentIOException(message, e);
    }
  }

  @Override
  public void onAcceptable(final SelectionKey key) {
    logger.debug("New connection request.");
    while (true) {
      SocketChannel channel = accept(key);
      if (channel == null) {
        break;
      }
      callback.accept(channel);
    }
  }

  @Override
  public void close() {
    Exceptions.ignore(serverSocketChannel::close,
        "The acceptor bound with " + localAddress + " closed.");
    logger.info("The acceptor bound with {} closed.", localAddress);
  }

  private SocketChannel accept(SelectionKey key) {
    logger.debug("New connection request.");
    logger.info("New connection request.");
    try {
      SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
      channel.configureBlocking(false);

      SSLEngine engine = context.createSSLEngine();
      engine.setUseClientMode(false);
      engine.beginHandshake();

      if (doHandshake(channel, engine)) {
        return channel;
      } else {
        channel.close();
        logger.debug("Connection closed due to handshake failure.");
        logger.info("Connection closed due to handshake failure.");
      }
    } catch (IOException e) {
      logger.error(e.getLocalizedMessage());
      e.printStackTrace();
      throw new InfluentIOException(e.getMessage(), e);
    }
    return null;
  }

  private boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {
    logger.debug("Handshaking...");
    logger.info("Handshaking...");
    SSLEngineResult result;
    SSLEngineResult.HandshakeStatus status;
    int bufferSize = engine.getSession().getApplicationBufferSize();
    ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
    ByteBuffer unwrappedBuffer = ByteBuffer.allocate(bufferSize);
    ByteBuffer wrappedBuffer = ByteBuffer.allocate(bufferSize);

    status = engine.getHandshakeStatus();
    logger.info("{}", status);
    while (status != SSLEngineResult.HandshakeStatus.FINISHED &&
        status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      switch (status) {
        case NEED_UNWRAP:
          if (socketChannel.read(buffer) < 0) {
            if (engine.isInboundDone() && engine.isOutboundDone()) {
              return false;
            }
            try {
              engine.closeInbound();
            } catch (SSLException e) {
              logger.error(e.getMessage());
            }
            engine.closeOutbound();
            status = engine.getHandshakeStatus();
            break;
          }
          buffer.flip();
          result = engine.unwrap(buffer, unwrappedBuffer);
          buffer.compact();
          status = engine.getHandshakeStatus();
          switch (result.getStatus()) {
            case OK:
              break;
            case BUFFER_OVERFLOW:
              unwrappedBuffer = ByteBuffer.allocate(unwrappedBuffer.capacity() * 2);
              break;
            case BUFFER_UNDERFLOW:
              // TODO
              break;
            case CLOSED:
              break;
            default:
              throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
          }
          break;
        case NEED_WRAP:
          wrappedBuffer.clear();
          result = engine.wrap(buffer, wrappedBuffer);
          status = result.getHandshakeStatus();
          switch (result.getStatus()) {
            case OK:
              wrappedBuffer.flip();
              while (wrappedBuffer.hasRemaining()) {
                socketChannel.write(wrappedBuffer);
              }
              break;
            case BUFFER_OVERFLOW:
              wrappedBuffer = ByteBuffer.allocate(wrappedBuffer.capacity() * 2);
              break;
            case BUFFER_UNDERFLOW:
              throw new SSLException("Buffer underflow after a wrap. Must not happen.");
            case CLOSED:
            default:
              throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
          }
          break;
        case NEED_TASK:
          Runnable task;
          while ((task = engine.getDelegatedTask()) != null) {
            executor.execute(task);
          }
          status = engine.getHandshakeStatus();
          break;
        case FINISHED:
          break;
        case NOT_HANDSHAKING:
          break;
        default:
          throw new IllegalStateException("Invalid SSL status: " + status);
      }
    }
    return true;
  }
}
