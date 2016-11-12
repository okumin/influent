package influent.forward;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioTcpAcceptor;

/**
 * A {@code ForwardServer} implemented by NIO.
 */
final class NioForwardServer implements ForwardServer {
  private final NioEventLoop eventLoop;

  /**
   * Creates a {@code NioForwardServer}.
   *
   * @param localAddress local address to be bound
   * @param callback invoked when a request arrives
   * @param chunkSizeLimit the allowable size of chunks
   * @param backlog backlog
   * @param sendBufferSize enqueue buffer size, it must be greater than or equal to zero
   * @param receiveBufferSize dequeue buffer size, it must be greater than or equal to zero
   * @param keepAliveEnabled whether SO_KEEPALIVE is enabled or not
   * @param tcpNoDelayEnabled whether TCP_NODELAY is enabled or not
   * @throws IllegalArgumentException if any of parameter is invalid
   *                                  e.g. the local address is already used
   * @throws influent.exception.InfluentIOException if some IO error occurs
   */
  NioForwardServer(final SocketAddress localAddress,
                   final ForwardCallback callback,
                   final long chunkSizeLimit,
                   final int backlog,
                   final int sendBufferSize,
                   final int receiveBufferSize,
                   final boolean keepAliveEnabled,
                   final boolean tcpNoDelayEnabled) {
    this.eventLoop = NioEventLoop.open();
    final Consumer<SocketChannel> channelFactory = socketChannel -> new NioForwardConnection(
        socketChannel, eventLoop, callback, chunkSizeLimit, sendBufferSize,
        keepAliveEnabled, tcpNoDelayEnabled
    );
    new NioTcpAcceptor(localAddress, eventLoop, channelFactory, backlog, receiveBufferSize);
    new NioUdpHeartbeatServer(localAddress, eventLoop);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run() {
    eventLoop.run();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Void> shutdown() {
    return eventLoop.shutdown();
  }
}
