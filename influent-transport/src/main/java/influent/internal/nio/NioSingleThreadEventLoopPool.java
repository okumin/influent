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

/** An {@code NioEventLoopPool} running one {@code NioEventLoop}. */
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

  /** {@inheritDoc} */
  @Override
  public void start(final ThreadFactory threadFactory) {
    threadFactory.newThread(eventLoop).start();
  }

  /** {@inheritDoc} */
  @Override
  public NioEventLoop next() {
    return eventLoop;
  }

  /** {@inheritDoc} */
  @Override
  public CompletableFuture<Void> shutdown() {
    return eventLoop.shutdown();
  }
}
