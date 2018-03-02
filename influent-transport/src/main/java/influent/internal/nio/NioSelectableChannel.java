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

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * A wrapped {@code SelectableChannel}.
 */
abstract class NioSelectableChannel {
  private SelectionKey key;

  /**
   * @return the underlying channel
   */
  abstract SelectableChannel unwrap();

  /**
   * @return the {@code SelectionKey}
   */
  final SelectionKey selectionKey() {
    return key;
  }

  /**
   * This is invoked when this channel is registered to a selector.
   *
   * @param key the {@code SelectionKey}
   * @throws IllegalStateException when this method is invoked more than once
   */
  final void onRegistered(final SelectionKey key) {
    if (this.key != null) {
      throw new IllegalStateException("This channel is registered more than once.");
    }
    this.key = key;
  }
}
