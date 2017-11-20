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

package influent.forward;

import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import influent.internal.nio.NioChannelConfig;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioEventLoopPool;
import influent.internal.nio.NioTcpAcceptor;
import influent.internal.nio.NioTcpConfig;

/**
 * A {@code ForwardServer} implemented by NIO.
 */
final class NioForwardServer implements ForwardServer {
  private final NioEventLoop bossEventLoop;
  private final NioEventLoopPool workerEventLoopPool;

  /**
   * Creates a {@code NioForwardServer}.
   *
   * @param localAddress local address to be bound
   * @param callback invoked when a request arrives
   * @param chunkSizeLimit the allowable size of chunks
   * @param tcpConfig the TCP config
   * @param workerPoolSize the event loop pool size for workers
   * @param channelConfig the channel configuration
   * @throws IllegalArgumentException if any of parameter is invalid
   *                                  e.g. the local address is already used
   * @throws influent.exception.InfluentIOException if some IO error occurs
   */
  NioForwardServer(final SocketAddress localAddress,
                   final ForwardCallback callback,
                   final long chunkSizeLimit,
                   final NioTcpConfig tcpConfig,
                   final int workerPoolSize,
                   final NioChannelConfig channelConfig,
                   final ForwardSecurity security) {
    bossEventLoop = NioEventLoop.open();
    workerEventLoopPool = NioEventLoopPool.open(workerPoolSize);
    final BiConsumer<SelectionKey, SocketChannel> channelFactory;
    if (channelConfig.isSslEnabled()) {
      channelFactory = (key, socketChannel) -> new NioSslForwardConnection(
          socketChannel, workerEventLoopPool.next(), callback,
          channelConfig.createSSLEngine(), chunkSizeLimit, tcpConfig
      );
    } else {
      channelFactory = (key, socketChannel) -> new NioForwardConnection(
          socketChannel, workerEventLoopPool.next(), callback, chunkSizeLimit, tcpConfig, security
      );
    }
    new NioTcpAcceptor(
        localAddress, bossEventLoop, channelFactory, tcpConfig
    );
    new NioUdpHeartbeatServer(localAddress, bossEventLoop);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void start(final ThreadFactory threadFactory) {
    workerEventLoopPool.start(threadFactory);
    threadFactory.newThread(bossEventLoop).start();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Void> shutdown() {
    return bossEventLoop.shutdown().thenCompose(x -> workerEventLoopPool.shutdown());
  }
}
