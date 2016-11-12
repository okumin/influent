package influent.internal.util;

import java.util.concurrent.CompletableFuture;

/**
 * Utilities for {@code CompletableFuture}.
 *
 * This is expected to be used in only Influent project.
 */
public final class Futures {
  private Futures() {
    throw new AssertionError();
  }

  /**
   * Creates a new {@code CompletableFuture} that will be completed
   * when the given original future is completed.
   * But the original future will not completed even when the new one is completed.
   *
   * {@code CompletableFuture}'s completion is a mutable state
   * and can be changed by anyone after the future is passed.
   * The owner of a future can pass that safely if using this function.
   *
   * @param original the original future
   * @param <T> the type of {@code CompletableFuture}'s value
   * @return the future that depends on the original future
   *         but the original future does not depend on
   */
  public static <T> CompletableFuture<T> followerOf(final CompletableFuture<T> original) {
    return original.thenApply(java.util.function.Function.identity());
  }
}
