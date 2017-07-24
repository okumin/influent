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
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import influent.internal.nio.NioAcceptorFactory;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioEventLoopPool;
import influent.internal.nio.NioChannelConfig;

import javax.net.ssl.SSLContext;

/**
 * A {@code ForwardServer} implemented by NIO.
 */
final class NioForwardServer implements ForwardServer {
  private final NioEventLoop bossEventLoop;
  private final NioEventLoopPool workerEventLoopPool;
  private final NioChannelConfig channelConfig;

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
   * @param workerPoolSize the event loop pool size for workers
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
                   final boolean tcpNoDelayEnabled,
                   final int workerPoolSize) {
    this(localAddress, callback, chunkSizeLimit, backlog,
        sendBufferSize, receiveBufferSize, keepAliveEnabled, tcpNoDelayEnabled,
        workerPoolSize, new NioChannelConfig());
  }

  NioForwardServer(final SocketAddress localAddress,
                   final ForwardCallback callback,
                   final long chunkSizeLimit,
                   final int backlog,
                   final int sendBufferSize,
                   final int receiveBufferSize,
                   final boolean keepAliveEnabled,
                   final boolean tcpNoDelayEnabled,
                   final int workerPoolSize,
                   final NioChannelConfig channelConfig) {
    bossEventLoop = NioEventLoop.open();
    workerEventLoopPool = NioEventLoopPool.open(workerPoolSize);
    this.channelConfig = channelConfig;
    final Consumer<SocketChannel> tcpChannelFactory = socketChannel -> new NioForwardConnection(
        socketChannel, workerEventLoopPool.next(), callback, chunkSizeLimit, sendBufferSize,
        keepAliveEnabled, tcpNoDelayEnabled, channelConfig
    );
    NioAcceptorFactory.create(
        localAddress, bossEventLoop, tcpChannelFactory, backlog, receiveBufferSize, channelConfig
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
