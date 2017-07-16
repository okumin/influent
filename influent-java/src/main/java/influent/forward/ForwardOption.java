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

package influent.forward;

import java.util.Objects;
import java.util.Optional;

final class ForwardOption {
  private static final ForwardOption EMPTY = new ForwardOption(null, null);

  private final String chunk;
  private final String compressed;

  private ForwardOption(final String chunk, final String compressed) {
    this.chunk = chunk;
    this.compressed = compressed;
  }

  static ForwardOption of(final String chunk, final String compressed) {
    return new ForwardOption(chunk, compressed);
  }

  static ForwardOption empty() {
    return EMPTY;
  }

  Optional<String> getChunk() {
    return Optional.ofNullable(chunk);
  }

  Optional<String> getCompressed() {
    return Optional.ofNullable(compressed);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForwardOption that = (ForwardOption) o;
    return Objects.equals(getChunk(), that.getChunk()) &&
        Objects.equals(getCompressed(), that.getCompressed());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getChunk(), getCompressed());
  }

  @Override
  public String toString() {
    return "ForwardOption(" + getChunk() + "," + getCompressed() + ")";
  }
}
