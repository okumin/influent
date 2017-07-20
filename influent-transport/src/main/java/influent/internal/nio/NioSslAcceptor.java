package influent.internal.nio;

import influent.exception.InfluentIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.function.Consumer;


public class NioSslAcceptor implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioSslAcceptor.class);
  private final SocketAddress localAddress;
  private final Consumer<SocketChannel> callback;
  private final ServerSocketChannel serverSocketChannel;
  private final SSLContext context;

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

  }

  private SocketChannel accept(SelectionKey key) {
    logger.debug("New connection request.");
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
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) {
    logger.debug("Handshaking...");
    SSLEngineResult result;
    SSLEngineResult.HandshakeStatus status;

    status = engine.getHandshakeStatus();
    while (status != SSLEngineResult.HandshakeStatus.FINISHED &&
        status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      switch (status) {
        case NEED_UNWRAP:
          break;
        case NEED_WRAP:
          break;
        case NEED_TASK:
          Runnable task;
          while ((task = engine.getDelegatedTask()) != null) {
            executor.execute(task);
          }
          break;
        case FINISHED:
          break;
        default:
          throw new IllegalStateException("Invalid SSL status: " + status);
      }
    }
    return true;
  }
}
