package influent.exception;

/**
 * IO error.
 */
public final class InfluentIOException extends RuntimeException {
  /**
   * Constructs a new {@code InfluentIOException}.
   *
   * @param message the error message
   * @param cause the cause
   */
  public InfluentIOException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new {@code InfluentIOException}.
   *
   * @param message the error message
   */
  public InfluentIOException(final String message) {
    super(message);
  }

  /**
   * Constructs a new {@code InfluentIOException}.
   */
  public InfluentIOException() {
    super();
  }
}
