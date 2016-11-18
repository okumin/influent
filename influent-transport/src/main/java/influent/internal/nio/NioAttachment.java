package influent.internal.nio;

import java.nio.channels.SelectionKey;

import influent.exception.InfluentIOException;

/**
 * An attachment for new IO operations.
 */
public interface NioAttachment extends AutoCloseable {
  /**
   * Handles a read event.
   * {@code NioAttachment} is closed when {@code onReadable} throws an exception.
   *
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException when some IO error occurs
   * @throws UnsupportedOperationException {@code onReadable} is not supported
   */
  default void onReadable(final SelectionKey key) {
    throw new UnsupportedOperationException(this + " does not support onReadable");
  }

  /**
   * Handles a write event.
   * {@code NioAttachment} is closed when {@code onWritable} throws an exception.
   *
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException when some IO error occurs
   * @throws UnsupportedOperationException {@code onWritable} is not supported
   */
  default void onWritable(final SelectionKey key) {
    throw new UnsupportedOperationException(this + " does not support onWritable");
  }

  /**
   * Handles an accept event.
   * {@code NioAttachment} is closed when {@code onAcceptable} throws an exception.
   *
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException when some IO error occurs
   * @throws UnsupportedOperationException {@code onAcceptable} is not supported
   */
  default void onAcceptable(final SelectionKey key) {
    throw new UnsupportedOperationException(this + " does not support onAcceptable");
  }

  /**
   * Handles a connect event.
   * {@code NioAttachment} is closed when {@code onConnectable} throws an exception.
   *
   * @param key the {@code SelectionKey}
   * @throws InfluentIOException when some IO error occurs
   * @throws UnsupportedOperationException {@code onConnectable} is not supported
   */
  default void onConnectable(final SelectionKey key) {
    throw new UnsupportedOperationException(this + " does not support onConnectable");
  }

  /**
   * Terminates this attachment.
   */
  void close();
}
