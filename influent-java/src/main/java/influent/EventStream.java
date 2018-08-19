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

package influent;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An event stream.
 *
 * <p>This is not immutable when the origin of {@code entries} is mutated. But instances of {@code
 * EventStream} that this library returns are guaranteed immutable.
 */
public final class EventStream {
  private final Tag tag;
  private final List<EventEntry> entries;

  private EventStream(final Tag tag, final List<EventEntry> entries) {
    this.tag = Objects.requireNonNull(tag);
    this.entries = Collections.unmodifiableList(Objects.requireNonNull(entries));
  }

  /**
   * Creates an {@code EventStream}.
   *
   * @param tag the tag
   * @param entries the entries
   * @return the new {@code EventStream}
   * @throws NullPointerException if the tag or the entries are null
   */
  public static EventStream of(final Tag tag, final List<EventEntry> entries) {
    return new EventStream(tag, entries);
  }

  /** @return the tag */
  public Tag getTag() {
    return tag;
  }

  /** @return the unmodifiable list of {@code EventEntry} */
  public List<EventEntry> getEntries() {
    return entries;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventStream that = (EventStream) o;
    return Objects.equals(getTag(), that.getTag())
        && Objects.equals(getEntries(), that.getEntries());
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(getTag(), getEntries());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "EventStream(" + tag + ", " + entries + ")";
  }
}
