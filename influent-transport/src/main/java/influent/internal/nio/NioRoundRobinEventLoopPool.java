package influent.internal.nio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@code NioEventLoopPool} running multiple {@code NioEventLoops}
 * and choose {@code NioEventLoop} in round-robin fashion.
 */
final class NioRoundRobinEventLoopPool implements NioEventLoopPool {
  private final NioEventLoop[] eventLoops;
  private final AtomicInteger counter = new AtomicInteger(0);

  NioRoundRobinEventLoopPool(final NioEventLoop[] eventLoops) {
    this.eventLoops = eventLoops;
  }

  /**
   * @return the new NioRoundRobinEventLoopPool
   * @throws influent.exception.InfluentIOException if some of event loops cannot be created
   */
  static NioEventLoopPool open(final int poolSize) {
    final NioEventLoop[] eventLoops = new NioEventLoop[poolSize];
    for (int i = 0; i < poolSize; ++i) {
      eventLoops[i] = NioEventLoop.open();
    }
    return new NioRoundRobinEventLoopPool(eventLoops);
  }

  @Override
  public void start(final ThreadFactory threadFactory) {
    for (final NioEventLoop eventLoop : eventLoops) {
      threadFactory.newThread(eventLoop).start();
    }
  }

  @Override
  public NioEventLoop next() {
    return eventLoops[Math.abs(counter.getAndIncrement() % eventLoops.length)];
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    final CompletableFuture[] results = new CompletableFuture[eventLoops.length];
    for (int i = 0; i < eventLoops.length; ++i) {
      results[i] = eventLoops[i].shutdown();
    }
    return CompletableFuture.allOf(results);
  }
}
