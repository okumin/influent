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

package influent.forward

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import influent._
import influent.exception.InfluentIOException
import influent.internal.msgpack.MsgpackStreamUnpacker
import influent.internal.nio.NioTcpChannel
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.stubbing.Answer1
import org.mockito.{AdditionalAnswers, ArgumentMatchers}
import org.msgpack.core.MessagePack
import org.msgpack.value.impl.ImmutableStringValueImpl
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioForwardConnectionSpec extends WordSpec with MockitoSugar {
  private[this] def success: CompletableFuture[Void] = {
    val future = new CompletableFuture[Void]()
    future.complete(null)
    future
  }
  private[this] def failure: CompletableFuture[Void] = {
    val future = new CompletableFuture[Void]()
    future.completeExceptionally(new RuntimeException)
    future
  }
  private[this] def response(chunk: String): ByteBuffer = {
    val stream = new ByteArrayOutputStream()
    val packer = MessagePack.newDefaultPacker(stream)
    packer.packMapHeader(1)
    packer.packString("ack")
    packer.packString(chunk)
    packer.flush()
    ByteBuffer.wrap(stream.toByteArray)
  }

  "onWritable" should {
    def createConnection(channel: NioTcpChannel): NioForwardConnection = {
      new NioForwardConnection(channel, mock[ForwardCallback], Int.MaxValue, mock[ForwardSecurity])
    }

    "send responses" in {
      val buffers = Seq(response("mofu1"), response("mofu2"), response("mofu3"))
      val channel = mock[NioTcpChannel]
      buffers.foreach { buffer =>
        when(channel.write(buffer))
          .thenAnswer(AdditionalAnswers.answer(new Answer1[Boolean, ByteBuffer] {
            override def answer(a: ByteBuffer): Boolean = {
              val size = a.remaining()
              a.position(a.limit())
              size > 0
            }
          }))
      }

      val connection = createConnection(channel)
      buffers.foreach { buffer => connection.responses.enqueue(buffer) }
      assert(connection.onWritable() === ())

      buffers.foreach { buffer =>
        buffer.rewind()
        verify(channel).write(buffer)
      }
      assert(!connection.responses.nonEmpty())
      verify(channel).disableOpWrite()
    }

    "not disable OP_WRITE" when {
      "all buffered responses are not sent" in {
        val buffers = Seq(response("mofu1"), response("mofu2"), response("mofu3"))
        val channel = mock[NioTcpChannel]
        when(channel.write(buffers(0)))
          .thenAnswer(AdditionalAnswers.answer(new Answer1[Boolean, ByteBuffer] {
            override def answer(a: ByteBuffer): Boolean = {
              val size = a.remaining()
              a.position(a.limit())
              size > 0
            }
          }))
        when(channel.write(buffers(1)))
          .thenAnswer(AdditionalAnswers.answer(new Answer1[Boolean, ByteBuffer] {
            override def answer(a: ByteBuffer): Boolean = {
              val size = a.remaining() - 1
              a.position(a.limit() - 1)
              size > 0
            }
          }))

        val connection = createConnection(channel)

        buffers.foreach { buffer => connection.responses.enqueue(buffer) }
        assert(connection.onWritable() === ())

        assert(buffers(0).remaining() === 0)
        assert(buffers(1).remaining() === 1)
        assert(buffers(2).remaining() === buffers(2).limit())

        assert(connection.responses.dequeue() eq buffers(1))
        assert(connection.responses.dequeue() eq buffers(2))
        assert(!connection.responses.nonEmpty())

        buffers.foreach(_.rewind())
        verify(channel).write(buffers(0))
        verify(channel).write(buffers(1))
        verifyNoMoreInteractions(channel)
      }
    }

    "fail with InfluentIOException" when {
      "it fails writing" in {
        val channel = mock[NioTcpChannel]
        when(channel.write(response("mofu"))).thenThrow(new InfluentIOException())

        val connection = createConnection(channel)

        connection.responses.enqueue(response("mofu"))
        assertThrows[InfluentIOException](connection.onWritable())

        verify(channel).write(response("mofu"))
      }
    }
  }

  "onReadable" should {
    "receive requests" in {
      val channel = mock[NioTcpChannel]

      val unpacker = mock[MsgpackStreamUnpacker]
      when(unpacker.hasNext).thenReturn(true, true, true, true, false)
      val dummyValue = new ImmutableStringValueImpl("mofu")
      when(unpacker.next()).thenReturn(dummyValue)
      when(channel.isOpen).thenReturn(true)

      val decoder = mock[MsgpackForwardRequestDecoder]
      val requests = Seq(
        Optional.of(ForwardRequest.of(
          EventStream.of(Tag.of("mofu1"), new util.LinkedList[EventEntry]()),
          ForwardOption.of("chunk1", null)
        )),
        Optional.of(ForwardRequest.of(
          EventStream.of(Tag.of("mofu2"), new util.LinkedList[EventEntry]()),
          ForwardOption.of(null, null)
        )),
        Optional.empty[ForwardRequest](),
        Optional.of(ForwardRequest.of(
          EventStream.of(Tag.of("mofu3"), new util.LinkedList[EventEntry]()),
          ForwardOption.of("chunk3", null)
        ))
      )
      requests.foldLeft(when(decoder.decode(dummyValue))) { (stub, request) =>
        stub.thenReturn(request)
      }

      val callback = mock[ForwardCallback]
      requests.filter(_.isPresent).foreach { request =>
        when(callback.consume(request.get().getStream)).thenReturn(success)
      }

      val security = mock[ForwardSecurity]
      val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)
      assert(connection.onReadable() === ())

      requests.filter(_.isPresent).foreach { request =>
        verify(callback).consume(request.get().getStream)
      }
      assert(connection.responses.dequeue() === response("chunk1"))
      assert(connection.responses.dequeue() === response("chunk3"))
      assert(!connection.responses.nonEmpty())
      verify(channel, times(2)).enableOpWrite()
      verify(channel, never()).close()
    }

    "send responses asynchronously" in {
      val channel = mock[NioTcpChannel]

      val unpacker = mock[MsgpackStreamUnpacker]
      when(unpacker.hasNext).thenReturn(true, false)
      val dummyValue = new ImmutableStringValueImpl("mofu")
      when(unpacker.next()).thenReturn(dummyValue)

      val decoder = mock[MsgpackForwardRequestDecoder]
      val request = ForwardRequest.of(
        EventStream.of(Tag.of("mofu1"), new util.LinkedList[EventEntry]()),
        ForwardOption.of("chunk1", null)
      )
      when(decoder.decode(dummyValue)).thenReturn(Optional.of(request))

      val callback = mock[ForwardCallback]
      when(callback.consume(request.getStream))
        .thenAnswer(AdditionalAnswers.answer(new Answer1[CompletableFuture[Void], EventStream] {
          override def answer(a: EventStream): CompletableFuture[Void] = {
            CompletableFuture.runAsync(() => Thread.sleep(1000))
          }
        }))

      val security = mock[ForwardSecurity]
      val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)
      assert(connection.onReadable() === ())
      verify(callback).consume(request.getStream)
      assert(!connection.responses.nonEmpty())

      Thread.sleep(1500)
      assert(connection.responses.nonEmpty())
      assert(connection.responses.dequeue() === response("chunk1"))
    }

    "not enable OP_WRITE" when {
      "all the request has no option" in {
        val channel = mock[NioTcpChannel]

        val unpacker = mock[MsgpackStreamUnpacker]
        when(unpacker.hasNext).thenReturn(true, false)
        val dummyValue = new ImmutableStringValueImpl("mofu")
        when(unpacker.next()).thenReturn(dummyValue)

        val decoder = mock[MsgpackForwardRequestDecoder]
        val request = ForwardRequest.of(
          EventStream.of(Tag.of("mofu1"), new util.LinkedList[EventEntry]()),
          ForwardOption.of(null, null)
        )
        when(decoder.decode(dummyValue)).thenReturn(Optional.of(request))

        val callback = mock[ForwardCallback]
        when(callback.consume(request.getStream)).thenReturn(success)

        val security = mock[ForwardSecurity]
        val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)
        assert(connection.onReadable() === ())
        verify(callback).consume(request.getStream)
      }
    }

    "close" when {
      "the stream terminates" in {
        val channel = mock[NioTcpChannel]
        val unpacker = mock[MsgpackStreamUnpacker]
        when(unpacker.hasNext).thenReturn(false)
        when(channel.isOpen).thenReturn(false)

        val callback = mock[ForwardCallback]
        val decoder = mock[MsgpackForwardRequestDecoder]
        val security = mock[ForwardSecurity]
        val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)

        assert(connection.onReadable() === ())
        verify(channel).close()
      }
    }

    "don't response" when {
      "the future fails" in {
        val channel = mock[NioTcpChannel]

        val unpacker = mock[MsgpackStreamUnpacker]
        when(unpacker.hasNext).thenReturn(true, false)
        val dummyValue = new ImmutableStringValueImpl("mofu")
        when(unpacker.next()).thenReturn(dummyValue)

        val decoder = mock[MsgpackForwardRequestDecoder]
        val request = ForwardRequest.of(
          EventStream.of(Tag.of("mofu1"), new util.LinkedList[EventEntry]()),
          ForwardOption.of("chunk1", null)
        )
        when(decoder.decode(dummyValue)).thenReturn(Optional.of(request))

        val callback = mock[ForwardCallback]
        when(callback.consume(request.getStream)).thenReturn(failure)

        val security = mock[ForwardSecurity]
        val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)
        assert(connection.onReadable() === ())
        verify(callback).consume(request.getStream)

        assert(!connection.responses.nonEmpty())
      }
    }

    "ignore invalid requests" in {
      val channel = mock[NioTcpChannel]

      val unpacker = mock[MsgpackStreamUnpacker]
      when(unpacker.hasNext).thenReturn(true, true, false)
      val dummyValue = new ImmutableStringValueImpl("mofu")
      when(unpacker.next()).thenReturn(dummyValue)

      val decoder = mock[MsgpackForwardRequestDecoder]
      val request = ForwardRequest.of(
        EventStream.of(Tag.of("mofu1"), new util.LinkedList[EventEntry]()),
        ForwardOption.of("chunk1", null)
      )
      when(decoder.decode(dummyValue))
        .thenThrow(new IllegalArgumentException)
        .thenReturn(Optional.of(request))

      val callback = mock[ForwardCallback]
      when(callback.consume(request.getStream)).thenReturn(success)

      val security = mock[ForwardSecurity]
      val connection = new NioForwardConnection(channel, callback, unpacker, decoder, security)
      assert(connection.onReadable() === ())

      verify(callback).consume(request.getStream)
      assert(connection.responses.dequeue() === response("chunk1"))
      assert(!connection.responses.nonEmpty())
      verify(channel).enableOpWrite()
    }

    "fail with InfluentIOException" when {
      "it fails reading" in {
        val callback = mock[ForwardCallback]
        val channel = mock[NioTcpChannel]
        val unpacker = mock[MsgpackStreamUnpacker]
        val security = mock[ForwardSecurity]
        when(unpacker.feed(any[Supplier[ByteBuffer]], ArgumentMatchers.eq[NioTcpChannel](channel)))
          .thenThrow(new InfluentIOException())
        val connection = new NioForwardConnection(
          channel, callback, unpacker, mock[MsgpackForwardRequestDecoder], security
        )

        assertThrows[InfluentIOException](connection.onReadable())
        verifyZeroInteractions(callback)
      }
    }
  }

  "close" should {
    "closes the channel" in {
      val channel = mock[NioTcpChannel]
      val security = mock[ForwardSecurity]
      val connection = new NioForwardConnection(
        channel, mock[ForwardCallback], Int.MaxValue, security
      )
      assert(connection.close() === ())
      verify(channel).close()
    }
  }
}
