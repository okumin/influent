package influent.internal.nio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

/**
 * An {@code NioEventLoopPool} running one {@code NioEventLoop}.
 */
final class NioSingleThreadEventLoopPool implements NioEventLoopPool {
  private final NioEventLoop eventLoop;

  NioSingleThreadEventLoopPool(final NioEventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  /**
   * @return the new NioSingleThreadEventLoopPool
   * @throws influent.exception.InfluentIOException if an event loop cannot be created
   */
  static NioEventLoopPool open() {
    return new NioSingleThreadEventLoopPool(NioEventLoop.open());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void start(final ThreadFactory threadFactory) {
    threadFactory.newThread(eventLoop).start();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public NioEventLoop next() {
    return eventLoop;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Void> shutdown() {
    return eventLoop.shutdown();
  }
}
