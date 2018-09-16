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

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.util.Optional

import influent.exception.InfluentIOException
import influent.internal.nio.NioUdpChannel
import influent.internal.nio.NioUdpChannel.Op
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioUdpHeartbeatServerSpec extends WordSpec with MockitoSugar {
  private[this] def response: ByteBuffer = {
    val buffer = ByteBuffer.allocate(1).put(0: Byte)
    buffer.flip()
    buffer
  }

  "onWritable" should {
    "flush the response buffer" in {
      val channel = mock[NioUdpChannel]
      val server = new NioUdpHeartbeatServer(channel)
      val targets = Seq(
        new InetSocketAddress(8001),
        new InetSocketAddress(8002),
        new InetSocketAddress(8003)
      )
      targets.foreach { target =>
        server.replyTo.enqueue(target)
        when(channel.send(response, target)).thenReturn(true)
      }

      assert(server.onWritable() === ())

      targets.foreach { target =>
        verify(channel).send(response, target)
      }
      verify(channel).disable(Op.WRITE)
      verifyNoMoreInteractions(channel)
    }

    "not disable OP_WRITE" when {
      "all responses are not flushed" in {
        val channel = mock[NioUdpChannel]
        val server = new NioUdpHeartbeatServer(channel)
        val targets = Seq(
          new InetSocketAddress(8001),
          new InetSocketAddress(8002),
          new InetSocketAddress(8003)
        )
        targets.foreach(server.replyTo.enqueue)
        when(channel.send(response, targets.head)).thenReturn(true)
        when(channel.send(response, targets(1))).thenReturn(false)

        assert(server.onWritable() === ())

        verify(channel).send(response, targets.head)
        verify(channel).send(response, targets(1))
        verifyNoMoreInteractions(channel)
      }
    }

    "not fail" when {
      "some IO error occurs" in {
        val channel = mock[NioUdpChannel]
        val server = new NioUdpHeartbeatServer(channel)
        val targets = Seq(
          new InetSocketAddress(8001),
          new InetSocketAddress(8002),
          new InetSocketAddress(8003)
        )
        targets.foreach(server.replyTo.enqueue)
        when(channel.send(response, targets.head)).thenReturn(true)
        when(channel.send(response, targets(1)))
          .thenThrow(new InfluentIOException()).thenReturn(true)
        when(channel.send(response, targets(2))).thenReturn(true)

        assert(server.onWritable() === ())

        verify(channel).send(response, targets.head)
        verify(channel, times(2)).send(response, targets(1))
        verify(channel).send(response, targets(2))
        verify(channel).disable(Op.WRITE)
        verifyNoMoreInteractions(channel)
      }
    }
  }

  "onReadable" should {
    "receives heartbeat requests" in {
      val channel = mock[NioUdpChannel]

      val server = new NioUdpHeartbeatServer(channel)
      val source1 = new InetSocketAddress(8001)
      val source2 = new InetSocketAddress(8002)
      when(channel.receive(ByteBuffer.allocate(1)))
        .thenReturn(Optional.of(source1), Optional.of(source2), Optional.empty())

      assert(server.onReadable() === ())

      verify(channel, times(3)).receive(ByteBuffer.allocate(1))
      assert(server.replyTo.dequeue() === source1)
      assert(server.replyTo.dequeue() === source2)
      assert(!server.replyTo.nonEmpty())
      verify(channel).enable(Op.WRITE)
      verifyNoMoreInteractions(channel)
    }

    "not fail" when {
      "some IO error occurs" in {
        val channel = mock[NioUdpChannel]
        val server = new NioUdpHeartbeatServer(channel)
        val source: SocketAddress = new InetSocketAddress(8000)
        when(channel.receive(ByteBuffer.allocate(1)))
          .thenThrow(new InfluentIOException())
          .thenReturn(Optional.of(source), Optional.empty())

        assert(server.onReadable() === ())
        verify(channel, times(3)).receive(ByteBuffer.allocate(1))
        assert(server.replyTo.dequeue() === source)
        assert(!server.replyTo.nonEmpty())
      }
    }
  }

  "close" should {
    "close the channel and inform the supervisor" in {
      val channel = mock[NioUdpChannel]
      val server = new NioUdpHeartbeatServer(channel)
      assert(server.close() === ())
      verify(channel).close()
    }
  }
}
