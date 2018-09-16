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
import java.util

import influent.internal.nio.NioTcpChannel.Op
import org.scalatest.WordSpec

class NioTcpChannelSpec extends WordSpec {
  "Op.bits" should {
    "compute the union of the given operations" in {
      assert(Op.bits(util.EnumSet.of(Op.READ)) === SelectionKey.OP_READ)
      assert(Op.bits(util.EnumSet.of(Op.WRITE)) === SelectionKey.OP_WRITE)
      assert(Op.bits(util.EnumSet.of(Op.READ, Op.WRITE)) ===
        (SelectionKey.OP_READ | SelectionKey.OP_WRITE))
      assert(Op.bits(util.EnumSet.of(Op.WRITE, Op.READ)) ===
        (SelectionKey.OP_READ | SelectionKey.OP_WRITE))
    }
  }
}
