package influent.internal.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thread-safe queue.
 * {@code ThreadSafeQueue} is designed for non-blocking applications,
 * so its APIs never block threads.
 *
 * This is expected to be used in only Influent project.
 *
 * {@code ThreadSafeQueue} is unconditionally thread-safe.
 *
 * @param <E> the type of elements
 */
public final class ThreadSafeQueue<E> {
  private final BlockingQueue<E> queue = new LinkedBlockingQueue<>();

  /**
   * Adds an element to this {@code ThreadSafeQueue}.
   *
   * @param element the element to add
   * @return {@code true} if the element was added to this queue, else
   *         {@code false}
   * @throws ClassCastException if the class of the specified element
   *         prevents it from being added to this queue
   * @throws NullPointerException if the specified element is null
   * @throws IllegalArgumentException if some property of the specified
   *         element prevents it from being added to this queue
   */
  public boolean enqueue(final E element) {
    return queue.offer(element);
  }

  /**
   * Removes the head element from this {@code ThreadSafeQueue}.
   *
   * @return the head element if this queue is non-empty, otherwise {@code null}
   */
  public E dequeue() {
    return queue.poll();
  }

  /**
   * Peeks the head element.
   *
   * @return the head element if this queue is non-empty, otherwise {@code null}
   */
  public E peek() {
    return queue.peek();
  }

  /**
   * Tests that this {@code ThreadSafeQueue} is non-empty.
   *
   * @return true if this {@code ThreadSafeQueue} is non-empty
   */
  public boolean nonEmpty() {
    return !queue.isEmpty();
  }
}
