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

import influent.EventStream;

final class ForwardRequest {
  private final EventStream stream;
  private final ForwardOption option;

  private ForwardRequest(final EventStream stream, final ForwardOption option) {
    this.stream = stream;
    this.option = option;
  }

  static ForwardRequest of(final EventStream stream, final ForwardOption option) {
    return new ForwardRequest(stream, option);
  }

  EventStream getStream() {
    return stream;
  }

  ForwardOption getOption() {
    return option;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForwardRequest that = (ForwardRequest) o;
    return Objects.equals(getStream(), that.getStream()) &&
        Objects.equals(getOption(), that.getOption());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getStream(), getOption());
  }

  @Override
  public String toString() {
    return "ForwardRequest(" + getStream() + "," + getOption() + ")";
  }
}
