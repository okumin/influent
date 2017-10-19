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

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import influent.exception.InfluentIOException;
import influent.internal.util.Exceptions;
import influent.internal.util.Futures;
import influent.internal.util.ThreadSafeQueue;

/**
 * An event loop for non-blocking IO.
 *
 * {@code NioEventLoop} is unconditionally thread-safe.
 * {@code NioEventLoop#run} is expected to be executed on one exclusive thread.
 */
public final class NioEventLoop implements Runnable {
  private enum State {
    IDLE, ACTIVE, STOPPING, TERMINATED
  }

  private static final Logger logger = LoggerFactory.getLogger(NioEventLoop.class);

  private final Selector selector;
  private final ThreadSafeQueue<NioEventLoopTask> tasks = new ThreadSafeQueue<>();
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

  NioEventLoop(final Selector selector) {
    this.selector = selector;
  }

  /**
   * Creates a new {@code NioEventLoop}.
   *
   * @return the {@code NioEventLoop}
   * @throws InfluentIOException if a selector cannot be created
   */
  public static NioEventLoop open() {
    try {
      return new NioEventLoop(Selector.open());
    } catch (final IOException e) {
      throw new InfluentIOException("A selector cannot be created.", e);
    }
  }

  /**
   * Executes this event loop.
   *
   * @throws IllegalStateException if this event loop has already started
   */
  @Override
  public void run() {
    if (!state.compareAndSet(State.IDLE, State.ACTIVE)) {
      throw new IllegalStateException("This NioEventLoop is " + state.get());
    }

    try {
      loop();
    } finally {
      cleanup();
      state.set(State.TERMINATED);
      shutdownFuture.complete(null);
    }
  }

  private void loop() {
    while (state.get() == State.ACTIVE) {
      while (tasks.nonEmpty()) {
        try {
          tasks.dequeue().run();
        } catch (final Exception e) {
          logger.error("NioEventLoopTask failed.", e);
        }
      }
      tasks.enqueue(new NioEventLoopTask.Select(selector));
    }
  }

  /**
   * Registers a channel with this event loop.
   * Operations are done asynchronously.
   *
   * @param channel the channel
   * @param ops the interest set
   * @param attachment the {@code NioAttachment}
   */
  public void register(final SelectableChannel channel, final int ops,
      final NioAttachment attachment) {
    addTask(new NioEventLoopTask.Register(selector, channel, ops, attachment));
  }

  /**
   * Enables the given interest set on the given {@code SelectionKey}.
   * Operations are done asynchronously.
   *
   * @param key the {@code SelectionKey}
   * @param ops the interest set to be enabled
   */
  public void enableInterestSet(final SelectionKey key, final int ops) {
    addTask(new NioEventLoopTask.UpdateInterestSet(key, current -> current | ops));
  }

  /**
   * Disables the given interest set on the given {@code SelectionKey}.
   * Operations are done asynchronously.
   *
   * @param key the {@code SelectionKey}
   * @param ops the interest set to be disabled
   */
  public void disableInterestSet(final SelectionKey key, final int ops) {
    addTask(new NioEventLoopTask.UpdateInterestSet(key, current -> current & ~ops));
  }

  private void addTask(final NioEventLoopTask task) {
    tasks.enqueue(task);
    // TODO: optimization
    selector.wakeup();
  }

  private void cleanup() {
    try {
      final Set<SelectionKey> keys = selector.keys();
      keys.forEach(key -> {
        final NioAttachment attachment = (NioAttachment) key.attachment();
        Exceptions.ignore(attachment::close, "Failed closing the attachment. " + attachment);
      });
      Exceptions.ignore(selector::close, "Failed closing the selector");
    } catch (final ClosedSelectorException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Stops this event loop.
   * Shutdown operations are executed asynchronously
   * and {@code NioEventLoop#shutdown} returns a {@code CompletedFuture}.
   *
   * @return {@code CompletableFuture} the future that will be completed when this event loop stops
   * @throws IllegalStateException when this event loop is not started
   */
  public CompletableFuture<Void> shutdown() {
    if (state.get() == State.IDLE) {
      throw new IllegalStateException("This NioEventLoop has not yet been started.");
    }
    if (state.compareAndSet(State.ACTIVE, State.STOPPING)) {
      selector.wakeup();
    }
    return Futures.followerOf(shutdownFuture);
  }
}
