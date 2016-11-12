package influent;

import java.time.Instant;
import java.util.Objects;

import org.msgpack.value.ImmutableMapValue;

/**
 * An event entry.
 *
 * This is not immutable when the {@code record} has mutable {@code org.msgpack.value.Value}.
 * But instances of {@code EventEntry} that this library returns are guaranteed immutable.
 */
public final class EventEntry {
  private final Instant time;
  private final ImmutableMapValue record;

  private EventEntry(final Instant time, final ImmutableMapValue record) {
    this.time = Objects.requireNonNull(time);
    this.record = Objects.requireNonNull(record);
  }

  /**
   * Creates an {@code EventEntry}.
   *
   * @param time the event time
   * @param record the record
   * @return the new {@code EventEntry}
   * @throws NullPointerException if the time or the record are null
   */
  public static EventEntry of(final Instant time, final ImmutableMapValue record) {
    return new EventEntry(time, record);
  }

  /**
   * @return the event time
   */
  public Instant getTime() {
    return time;
  }

  /**
   * @return the record
   */
  public ImmutableMapValue getRecord() {
    return record;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventEntry that = (EventEntry) o;
    return Objects.equals(getTime(), that.getTime()) &&
        Objects.equals(getRecord(), that.getRecord());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(getTime(), getRecord());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "EventEntry(" + getTime() + ',' + getRecord() + ')';
  }
}
