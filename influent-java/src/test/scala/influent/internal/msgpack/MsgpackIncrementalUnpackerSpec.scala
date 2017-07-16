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

package influent.internal.msgpack

import influent.internal.msgpack.MsgpackUnpackerArbitrary._
import java.nio.ByteBuffer
import org.msgpack.core.MessagePack
import org.msgpack.value.ImmutableValue
import org.scalacheck.Shrink
import org.scalatest.WordSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class MsgpackIncrementalUnpackerSpec extends WordSpec with GeneratorDrivenPropertyChecks {
  "MsgpackIncrementalUnpacker" should {
    "decode msgpack values" in {
      implicit val shrinkValue: Shrink[ImmutableValue] = Shrink.shrinkAny
      implicit val shrinkInt: Shrink[Int] = Shrink.shrinkAny

      forAll { (value: ImmutableValue, groupSize: Int) =>
        whenever(groupSize > 0) {
          val packer = MessagePack.newDefaultBufferPacker()
          packer.packValue(value)
          val asBytes = packer.toByteArray

          var unpacker: MsgpackIncrementalUnpacker = FormatUnpacker.getInstance()
          val buffer = new InfluentByteBuffer(Int.MaxValue)
          val chunks = asBytes.grouped(groupSize).toList
          chunks.init.foreach { bytes =>
            val buf = ByteBuffer.allocate(1024)
            buf.put(bytes)
            buffer.push(buf)
            val result = unpacker.unpack(buffer)
            assert(!result.isCompleted)
            unpacker = result.next()
          }

          val buf = ByteBuffer.allocate(1024)
          buf.put(chunks.last)
          buffer.push(buf)

          val actual = unpacker.unpack(buffer)
          assert(actual.isCompleted)
          assert(actual.value() === value)
        }
      }
    }
  }
}
