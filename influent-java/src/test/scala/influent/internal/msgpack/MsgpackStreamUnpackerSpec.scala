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

import influent.exception.InfluentIOException
import influent.internal.nio.NioTcpChannel
import java.nio.ByteBuffer
import java.util.function.Supplier
import org.mockito.Mockito._
import org.msgpack.core.MessagePack
import org.scalacheck.{Gen, Shrink}
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import scala.collection.mutable

class MsgpackStreamUnpackerSpec
  extends WordSpec
    with GeneratorDrivenPropertyChecks
    with MockitoSugar {
  "MsgpackStreamUnpacker" should {
    "consume and unpack" in {
      val gen = for {
        messages <- Gen.listOf(Gen.alphaStr.filter(_.length < 4096))
        split <- Gen.chooseNum(1, 128)
      } yield {
        val packer = MessagePack.newDefaultBufferPacker()
        messages.foreach(packer.packString)
        val bytes = packer.toByteArray
        (messages, bytes.grouped(split).toList)
      }

      implicit val shrink: Shrink[(List[String], List[Array[Byte]])] = Shrink.shrinkAny
      forAll(gen) {
        case (messages, chunks) =>
          val channel = mock[NioTcpChannel]
          // extra insufficient bytes
          val extra = {
            val packer = MessagePack.newDefaultBufferPacker()
            packer.packArrayHeader(10)
            packer.toByteArray
          }
          val supplier = new Supplier[ByteBuffer] {
            val iterator = (chunks :+ extra).map { bytes =>
              val buffer = ByteBuffer.allocate(1024 * 1024)
              buffer.put(bytes).flip()
              buffer
            }.toIterator

            override def get(): ByteBuffer = {
              if (iterator.hasNext) iterator.next() else null
            }
          }

          val unpacker = new MsgpackStreamUnpacker(Int.MaxValue)
          assert(unpacker.feed(supplier, channel) === ())

          val values = (1 to messages.size).map { _ =>
            assert(unpacker.hasNext)
            unpacker.next()
          }.map(_.asStringValue().asString())
          assert(values === messages)

          assert(!unpacker.hasNext)
          assertThrows[NoSuchElementException](unpacker.next())
          verify(channel, never()).close()
      }
    }

    "consume incrementally" in {
      val channel = mock[NioTcpChannel]

      val message = "123456789"
      val packets = {
        val packer = MessagePack.newDefaultBufferPacker()
        packer.packString(message)
        val bytes = packer.toByteArray
        bytes.splitAt(4)
      }

      val supplier = new Supplier[ByteBuffer] {
        var i = 0

        override def get(): ByteBuffer = {
          i += 1
          i match {
            case 1 =>
              val buffer = ByteBuffer.allocate(1024 * 1024)
              buffer.put(packets._1).flip()
              buffer
            case 2 | 4 => null
            case 3 =>
              val buffer = ByteBuffer.allocate(1024 * 1024)
              buffer.put(packets._2).flip()
              buffer
          }
        }
      }

      val unpacker = new MsgpackStreamUnpacker(Int.MaxValue)
      assert(unpacker.feed(supplier, channel) === ())

      assert(!unpacker.hasNext)
      assertThrows[NoSuchElementException](unpacker.next())

      assert(unpacker.feed(supplier, channel) === ())
      assert(unpacker.hasNext)
      assert(unpacker.next().asStringValue().asString() === message)
      verify(channel, never()).close()
    }

    "handle messages which size is less than or equal to the limit" in {
      val gen = for {
        limit <- Gen.chooseNum(1000, 2048) // header is 3 bytes
        strings <- Gen.listOf(Gen.listOf(Gen.alphaChar).map(_.mkString))
        limitNotExceeded = strings.filter(_.length <= limit)
        if limitNotExceeded.nonEmpty
        groupSize <- Gen.chooseNum(1, 1024) // less then buffer size
      } yield (limitNotExceeded, limit, groupSize)

      implicit val shrink: Shrink[(List[String], Int, Int)] = Shrink.shrinkAny
      forAll(gen) {
        case (strings, limit, groupSize) =>
          val packer = MessagePack.newDefaultBufferPacker()
          strings.foreach(packer.packString)
          val groupedBytes = packer.toByteArray.grouped(groupSize)

          val channel = mock[NioTcpChannel]
          val supplier = new Supplier[ByteBuffer] {
            override def get(): ByteBuffer = {
              if (groupedBytes.hasNext) {
                val buffer = ByteBuffer.allocate(1024 * 16)
                buffer.put(groupedBytes.next()).flip()
                buffer
              } else {
                null
              }
            }
          }
          val unpacker = new MsgpackStreamUnpacker(limit)

          assert(unpacker.feed(supplier, channel) === ())
          strings.foreach { string =>
            assert(unpacker.hasNext)
            assert(unpacker.next().asStringValue().asString() === string)
          }
          assert(!unpacker.hasNext)
      }
    }
  }

  "feed" should {
    "decode a large string" in {
      val packer = MessagePack.newDefaultBufferPacker()
      packer.packString("1" * 65536)
      val bytes = packer.toByteArray
      val groupedBytes = bytes.grouped(1024)

      val channel = mock[NioTcpChannel]
      val supplier = new Supplier[ByteBuffer] {
        override def get(): ByteBuffer = {
          if (groupedBytes.hasNext) {
            val buffer = ByteBuffer.allocate(1024 * 1024)
            buffer.put(groupedBytes.next()).flip()
            buffer
          } else {
            null
          }
        }
      }
      val unpacker = new MsgpackStreamUnpacker(Int.MaxValue)

      assert(unpacker.feed(supplier, channel) === ())
      assert(unpacker.next().asStringValue().asString() === "1" * 65536)
      assert(!unpacker.hasNext)
      verify(channel, never()).close()
    }

    "fail with InfluentIOException" when {
      "the chunk size exceeds the limit" in {
        val channel = mock[NioTcpChannel]
        val packer = MessagePack.newDefaultBufferPacker()
        // each string has 3 bytes header
        packer
          .packString("1" * 1000)
          .packString("2" * 1001)
          .packString("3" * 1021)
          .packString("4" * 1022)
        val bytes = packer.toByteArray
        val queue = mutable.Queue(
          bytes.slice(0, 1002), // insufficient
          Array.empty[Byte],
          bytes.slice(1002, 2007), // unpack 2 messages
          bytes.slice(2007, 3031), // the same size as the limit
          Array.empty[Byte],
          bytes.slice(3031, 4055), // limit exceeded
          bytes.slice(4055, 4056) // unconsumed
        )

        val supplier = new Supplier[ByteBuffer] {
          override def get(): ByteBuffer = {
            if (queue.isEmpty) {
              null
            } else {
              val buffer = ByteBuffer.allocate(1024)
              buffer.put(queue.dequeue()).flip()
              if (buffer.hasRemaining) buffer else null
            }
          }
        }
        val unpacker = new MsgpackStreamUnpacker(1024)

        assert(unpacker.feed(supplier, channel) === ())
        assert(!unpacker.hasNext)

        assert(unpacker.feed(supplier, channel) === ())
        assert(unpacker.next().asStringValue().asString() === "1" * 1000)
        assert(unpacker.next().asStringValue().asString() === "2" * 1001)
        assert(unpacker.next().asStringValue().asString() === "3" * 1021)
        assert(!unpacker.hasNext)

        assertThrows[InfluentIOException](unpacker.feed(supplier, channel))
        assert(!unpacker.hasNext)
        verify(channel).close()
      }
    }
  }
}
