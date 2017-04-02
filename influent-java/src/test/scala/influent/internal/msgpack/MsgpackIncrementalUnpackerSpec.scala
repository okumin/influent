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
