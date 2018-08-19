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

import java.util.Objects;

/**
 * A tag of fluentd's event.
 *
 * <p>Instances of {@code Tag} are immutable.
 */
public final class Tag implements Comparable<Tag> {
  private final String name;

  private Tag(final String name) {
    this.name = Objects.requireNonNull(name);
  }

  /**
   * Creates a {@code Tag}.
   *
   * @param name the tag name
   * @return the tag with the given name
   * @throws NullPointerException if the name is null
   */
  public static Tag of(final String name) {
    return new Tag(name);
  }

  /** @return the tag name */
  public String getName() {
    return name;
  }

  /**
   * Compares two tags lexicographically.
   *
   * @param o the {@code Tag} to be compared
   * @return the value 0 if the name of this tag is equal to that of the argument a value less than
   *     0 if the name of this tag is lexicographically less than that of the argument a value
   *     greater than 0 if the name of this tag is lexicographically greater than that of the
   *     argument
   */
  @Override
  public int compareTo(final Tag o) {
    return getName().compareTo(o.getName());
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
    final Tag tag = (Tag) o;
    return Objects.equals(getName(), tag.getName());
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(getName());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "Tag(" + getName() + ')';
  }
}
