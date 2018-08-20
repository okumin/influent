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

package influent.internal.nio;

import java.nio.channels.SelectionKey;

/**
 * A wrapped {@code SelectionKey}.
 *
 * <p>{@code NioSelectionKey} has a feature to bind the underlying {@code SelectionKey} lazily.
 *
 * <p>{@code NioSelectionKey} is not thread-safe. All the method should be invoked by an event loop
 * thread.
 */
final class NioSelectionKey {
  private SelectionKey key;

  private NioSelectionKey() {}

  static NioSelectionKey create() {
    return new NioSelectionKey();
  }

  SelectionKey unwrap() {
    return key;
  }

  void bind(final SelectionKey key) {
    if (this.key != null) {
      throw new IllegalStateException("This NioSelectionKey is already bound.");
    }
    this.key = key;
  }
}
