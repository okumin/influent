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

import influent.exception.InfluentIOException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, SelectionKey}
import java.util.Optional
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioUdpChannelSpec extends WordSpec with MockitoSugar {
  "send" should {
    "send and return true" in {
      val datagramChannel = mock[DatagramChannel]
      val channel = new NioUdpChannel(datagramChannel)

      val src = ByteBuffer.allocate(8)
      val target = new InetSocketAddress(8000)
      when(datagramChannel.send(src, target)).thenReturn(8)

      val actual = channel.send(src, target)
      assert(actual)
      verify(datagramChannel).send(src, target)
      verify(datagramChannel, never()).close()
    }

    "not send and return false" when {
      "there is no sufficient room in the socket buffer" in {
        val datagramChannel = mock[DatagramChannel]
        val channel = new NioUdpChannel(datagramChannel)

        val src = ByteBuffer.allocate(8)
        val target = new InetSocketAddress(8000)
        when(datagramChannel.send(src, target)).thenReturn(0)

        val actual = channel.send(src, target)
        assert(!actual)
        verify(datagramChannel).send(src, target)
        verify(datagramChannel, never()).close()
      }
    }

    "fail with InfluentIOException" when {
      "some IO error occurs" in {
        val datagramChannel = mock[DatagramChannel]
        val channel = new NioUdpChannel(datagramChannel)

        val src = ByteBuffer.allocate(8)
        val target = new InetSocketAddress(8000)
        when(datagramChannel.send(src, target)).thenThrow(new IOException)

        assertThrows[InfluentIOException](channel.send(src, target))
        verify(datagramChannel).send(src, target)
        verify(datagramChannel).close()
      }
    }
  }

  "receive" should {
    "return SocketAddress" in {
      val datagramChannel = mock[DatagramChannel]
      val channel = new NioUdpChannel(datagramChannel)

      val dst = ByteBuffer.allocate(8)
      val expected = new InetSocketAddress(8000)
      when(datagramChannel.receive(dst)).thenReturn(expected)

      val actual = channel.receive(dst)
      assert(actual.get() === expected)
      verify(datagramChannel).receive(dst)
      verify(datagramChannel, never()).close()
    }

    "return Optional.empty()" when {
      "no datagram is available" in {
        val datagramChannel = mock[DatagramChannel]
        val channel = new NioUdpChannel(datagramChannel)

        val dst = ByteBuffer.allocate(8)
        when(datagramChannel.receive(dst)).thenReturn(null)

        val actual = channel.receive(dst)
        assert(actual === Optional.empty())
        verify(datagramChannel).receive(dst)
        verify(datagramChannel, never()).close()
      }
    }

    "fail with InfluentIOException" when {
      "some IO error occurs" in {
        val datagramChannel = mock[DatagramChannel]
        val channel = new NioUdpChannel(datagramChannel)

        val dst = ByteBuffer.allocate(8)
        when(datagramChannel.receive(dst)).thenThrow(new IOException())

        assertThrows[InfluentIOException](channel.receive(dst))
        verify(datagramChannel).receive(dst)
        verify(datagramChannel).close()
      }
    }
  }

  "register" should {
    "registers this channel to the event loop" in {
      val datagramChannel = mock[DatagramChannel]
      val channel = new NioUdpChannel(datagramChannel)

      val eventLoop = mock[NioEventLoop]
      val ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE
      val attachment = mock[NioAttachment]

      assert(channel.register(eventLoop, true, false, attachment) === ())
      verify(eventLoop).register(channel, SelectionKey.OP_READ, attachment)

      assert(channel.register(eventLoop, false, true, attachment) === ())
      verify(eventLoop).register(channel, SelectionKey.OP_WRITE, attachment)

      assert(channel.register(eventLoop, true, true, attachment) === ())
      verify(eventLoop).register(channel, SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment)
    }
  }

  "enableOpWrite" should {
    "enable OP_WRITE" in {
      val channel = new NioUdpChannel(mock[DatagramChannel])
      val key = mock[SelectionKey]
      val eventLoop = mock[NioEventLoop]
      channel.onRegistered(key)
      assert(channel.enableOpWrite(eventLoop) === ())
      verify(eventLoop).enableInterestSet(key, SelectionKey.OP_WRITE)
    }
  }

  "disableOpWrite" should {
    "disable OP_WRITE" in {
      val channel = new NioUdpChannel(mock[DatagramChannel])
      val key = mock[SelectionKey]
      val eventLoop = mock[NioEventLoop]
      channel.onRegistered(key)
      assert(channel.disableOpWrite(eventLoop) === ())
      verify(eventLoop).disableInterestSet(key, SelectionKey.OP_WRITE)
    }
  }

  "close" should {
    "close the datagram channel" in {
      val datagramChannel = mock[DatagramChannel]
      val channel = new NioUdpChannel(datagramChannel)

      assert(channel.close() === ())
      verify(datagramChannel).close()
    }

    "not fail" when {
      "closing the datagram channel fails" in {
        val datagramChannel = mock[DatagramChannel]
        when(datagramChannel.close()).thenThrow(new IOException())
        val channel = new NioUdpChannel(datagramChannel)

        assert(channel.close() === ())
        verify(datagramChannel).close()
      }
    }
  }

  "unwrap" should {
    "return the underlying channel" in {
      val datagramChannel = mock[DatagramChannel]
      val channel = new NioUdpChannel(datagramChannel)
      assert(channel.unwrap() === datagramChannel)
    }
  }
}
