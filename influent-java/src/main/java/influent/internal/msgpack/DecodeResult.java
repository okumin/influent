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

package influent.internal.msgpack;

import org.msgpack.value.ImmutableValue;

abstract class DecodeResult {
  private static final class Complete extends DecodeResult {
    private final ImmutableValue value;

    Complete(final ImmutableValue value) {
      this.value = value;
    }

    @Override
    boolean isCompleted() {
      return true;
    }

    @Override
    MsgpackIncrementalUnpacker next() {
      throw new IllegalStateException();
    }

    @Override
    ImmutableValue value() {
      return value;
    }
  }

  private static final class Continue extends DecodeResult {
    private final MsgpackIncrementalUnpacker next;

    Continue(final MsgpackIncrementalUnpacker next) {
      this.next = next;
    }

    @Override
    boolean isCompleted() {
      return false;
    }

    @Override
    MsgpackIncrementalUnpacker next() {
      return next;
    }

    @Override
    ImmutableValue value() {
      throw new IllegalStateException();
    }
  }

  static DecodeResult complete(final ImmutableValue result) {
    return new Complete(result);
  }

  static DecodeResult next(final MsgpackIncrementalUnpacker next) {
    return new Continue(next);
  }

  abstract boolean isCompleted();

  abstract MsgpackIncrementalUnpacker next();

  abstract ImmutableValue value();
}
