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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

/**
 * A pool of {@code NioEventLoop}.
 *
 * <p>{@code NioEventLoop} is unconditionally thread-safe.
 */
public interface NioEventLoopPool {
  /**
   * Creates the new {@code NioEventLoopPool} which contains the given size of {@code
   * NioEventLoops}. The larger {@code poolSize} is given, the larger number of threads concurrently
   * run.
   *
   * @param poolSize the size of {@code NioEventLoopPool}
   * @return the new {@code NioEventLoopPool}
   * @throws influent.exception.InfluentIOException if some of event loops cannot be created
   */
  static NioEventLoopPool open(final int poolSize) {
    if (poolSize <= 0) {
      throw new IllegalArgumentException("poolSize must be greater than 0.");
    }
    if (poolSize == 1) {
      return NioSingleThreadEventLoopPool.open();
    } else {
      return NioRoundRobinEventLoopPool.open(poolSize);
    }
  }

  /**
   * Starts all the event loop.
   *
   * @param threadFactory the factory of event loop threads
   * @throws IllegalStateException if this event loop has already started
   */
  void start(final ThreadFactory threadFactory);

  /** @return the next {@code NioEventLoop} */
  NioEventLoop next();

  /**
   * Stops all the {@code NioEventLoop}. Shutdown operations are executed asynchronously and {@code
   * NioEventLoopPool#shutdown} returns a {@code CompletedFuture}.
   *
   * @return {@code CompletableFuture} the future that will be completed when all the event loop
   *     stops
   * @throws IllegalStateException when this event loop pool is not started
   */
  CompletableFuture<Void> shutdown();
}
