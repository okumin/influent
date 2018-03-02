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

package influent.internal.nio

import java.nio.channels.{SelectableChannel, SelectionKey}
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioSelectableChannelSpec extends WordSpec with MockitoSugar {
  final class TestNioSelectableChannel extends NioSelectableChannel {
    override def unwrap(): SelectableChannel = sys.error("not implemented")
  }

  "onRegistered" should {
    "set the SelectionKey" in {
      val channel = new TestNioSelectableChannel
      assert(channel.selectionKey() === null)
      val key = mock[SelectionKey]
      channel.onRegistered(key)
      assert(channel.selectionKey() === key)
    }

    "fail with IllegalStateException" when {
      "it is invoked more than once" in {
        val channel = new TestNioSelectableChannel
        val key = mock[SelectionKey]
        channel.onRegistered(key)
        assert(channel.selectionKey() === key)
        assertThrows[IllegalStateException] {
          channel.onRegistered(key)
        }
        assertThrows[IllegalStateException] {
          channel.onRegistered(key)
        }
      }
    }
  }
}
