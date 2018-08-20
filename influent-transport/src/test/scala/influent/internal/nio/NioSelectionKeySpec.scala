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

import java.nio.channels.SelectionKey

import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioSelectionKeySpec extends WordSpec with MockitoSugar {
  "bind" should {
    "set the underlying SelectionKey" when {
      "the NioSelectionKey is not yet bound" in {
        val javaKey = mock[SelectionKey]
        val key = NioSelectionKey.create()
        assert(key.bind(javaKey) === ())
        assert(key.unwrap() === javaKey)
      }
    }

    "fail" when {
      "the NioSelectionKey is already bound" in {
        val javaKey1 = mock[SelectionKey]
        val javaKey2 = mock[SelectionKey]
        val key = NioSelectionKey.create()

        assert(key.bind(javaKey1) === ())
        assert(key.unwrap() === javaKey1)

        assertThrows[IllegalStateException] {
          key.bind(javaKey2)
        }
        assert(key.unwrap() === javaKey1)
      }
    }
  }
}
