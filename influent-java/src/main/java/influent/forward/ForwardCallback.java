package influent.forward;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import influent.EventStream;

/**
 * The callback function that consumes {@code EventStreams}.
 */
@FunctionalInterface
public interface ForwardCallback {
  /**
   * Creates the {@code ForwardCallback}.
   * See also ForwardCallback#consume.
   *
   * @param consumer the callback function
   * @return the {@code ForwardCallback}
   */
  static ForwardCallback of(final Function<EventStream, CompletableFuture<Void>> consumer) {
    return consumer::apply;
  }

  /**
   * Creates a {@code ForwardCallback} from the synchronous {@code Consumer} and the {@code Executor}.
   *
   * @param consumer the synchronous {@code Consumer}
   * @param executor the {@code Executor} that executes {@code consumer}
   * @return the {@code ForwardCallback}
   */
  static ForwardCallback ofSyncConsumer(final Consumer<EventStream> consumer,
                                        final Executor executor) {
    return stream -> CompletableFuture.runAsync(() -> consumer.accept(stream), executor);
  }

  /**
   * Consumes an {@code EventStream}.
   *
   * {@code ForwardCallback#consume} must not be blocked
   * since it is invoked on an event loop thread.
   * If there are some IO operation or a CPU intensive processing,
   * those must be executed on the another thread.
   *
   * This method receives an {@code EventStream} and returns a {@code CompletableFuture}.
   * When the {@code CompletableFuture} succeeds,
   * Influent assumes that the {@code EventStream} is completely consumed and
   * may send an ack response to the client.
   *
   * When the {@code CompletableFuture} succeeds, Influent never sends an ack.
   *
   * @param stream the {@code EventStream}
   * @return the result of this consumption
   */
  CompletableFuture<Void> consume(EventStream stream);
}
