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

package influent.internal.util;

import java.util.concurrent.CompletableFuture;

/**
 * Utilities for {@code CompletableFuture}.
 *
 * <p>This is expected to be used in only Influent project.
 */
public final class Futures {
  private Futures() {
    throw new AssertionError();
  }

  /**
   * Creates a new {@code CompletableFuture} that will be completed when the given original future
   * is completed. But the original future will not completed even when the new one is completed.
   *
   * <p>{@code CompletableFuture}'s completion is a mutable state and can be changed by anyone after
   * the future is passed. The owner of a future can pass that safely if using this function.
   *
   * @param original the original future
   * @param <T> the type of {@code CompletableFuture}'s value
   * @return the future that depends on the original future but the original future does not depend
   *     on
   */
  public static <T> CompletableFuture<T> followerOf(final CompletableFuture<T> original) {
    return original.thenApply(java.util.function.Function.identity());
  }
}
