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

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, SelectionKey, SocketChannel}

import influent.exception.InfluentIOException
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioTcpPlaintextChannelSpec extends WordSpec with MockitoSugar {
  private[this] val remoteAddress = new InetSocketAddress("127.0.0.1", 24224)

  "write" should {
    "write bytes to the channel" in {
      val src = ByteBuffer.allocate(8)
      val socketChannel = mock[SocketChannel]
      when(socketChannel.write(src)).thenReturn(4)
      val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

      assert(channel.write(src) === true)
      verify(socketChannel).write(src)
      verify(socketChannel, never()).close()
    }

    "fail with InfluentIOException" when {
      "it fails writing" in {
        val src = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.write(src)).thenThrow(new IOException())
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

        assertThrows[InfluentIOException](channel.write(src))
        verify(socketChannel).write(src)
        verify(socketChannel).close()
      }
    }
  }

  "read" should {
    "read bytes from the channel" in {
      val dst = ByteBuffer.allocate(8)
      val socketChannel = mock[SocketChannel]
      when(socketChannel.read(dst)).thenReturn(4)
      val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

      assert(channel.read(dst) === true)
      verify(socketChannel, never()).close()
    }

    "do nothing" when {
      "the socket buffer is empty" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenReturn(0)
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

        assert(channel.read(dst) === false)
        verify(socketChannel, never()).close()
      }
    }

    "close channel" when {
      "the channel returns -1" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenReturn(-1)
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

        assert(channel.read(dst) === false)
        verify(socketChannel).close()
      }

      "the stream is completed" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenThrow(new ClosedChannelException())
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

        assert(channel.read(dst) === false)
        verify(socketChannel).close()
      }
    }

    "fail with InfluentIOException" when {
      "it fails reading" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenThrow(new IOException())
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

        assertThrows[InfluentIOException](channel.read(dst))
        verify(socketChannel).close()
      }
    }
  }

  "register" should {
    "registers this channel to the event loop" in {
      val socketChannel = mock[SocketChannel]
      val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)

      val eventLoop = mock[NioEventLoop]
      val attachment = mock[NioAttachment]

      assert(channel.register(eventLoop, true, false, attachment) === ())
      verify(eventLoop).register(socketChannel, channel.key, SelectionKey.OP_READ, attachment)

      assert(channel.register(eventLoop, false, true, attachment) === ())
      verify(eventLoop).register(socketChannel, channel.key, SelectionKey.OP_WRITE, attachment)

      assert(channel.register(eventLoop, true, true, attachment) === ())
      verify(eventLoop).register(socketChannel, channel.key, SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment)
    }
  }

  "enableOpRead" should {
    "enable OP_READ" in {
      val channel = new NioTcpPlaintextChannel(mock[SocketChannel], remoteAddress)
      val eventLoop = mock[NioEventLoop]
      assert(channel.enableOpRead(eventLoop) === ())
      verify(eventLoop).enableInterestSet(channel.key, SelectionKey.OP_READ)
    }
  }

  "enableOpWrite" should {
    "enable OP_WRITE" in {
      val channel = new NioTcpPlaintextChannel(mock[SocketChannel], remoteAddress)
      val eventLoop = mock[NioEventLoop]
      assert(channel.enableOpWrite(eventLoop) === ())
      verify(eventLoop).enableInterestSet(channel.key, SelectionKey.OP_WRITE)
    }
  }

  "disableOpWrite" should {
    "disable OP_WRITE" in {
      val channel = new NioTcpPlaintextChannel(mock[SocketChannel], remoteAddress)
      val eventLoop = mock[NioEventLoop]
      assert(channel.disableOpWrite(eventLoop) === ())
      verify(eventLoop).disableInterestSet(channel.key, SelectionKey.OP_WRITE)
    }
  }

  "close" should {
    "close the socket channel" in {
      val socketChannel = mock[SocketChannel]
      val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)
      assert(channel.close() === ())
      verify(socketChannel).close()
    }

    "not fail" when {
      "it fails closing the socket channel" in {
        val socketChannel = mock[SocketChannel]
        when(socketChannel.close()).thenThrow(new IOException)
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)
        assert(channel.close() === ())
        verify(socketChannel).close()
      }
    }
  }

  "isOpen" should {
    "return true" when {
      "the channel is open" in {
        val socketChannel = mock[SocketChannel]
        when(socketChannel.isOpen).thenReturn(true)
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)
        assert(channel.isOpen)
      }
    }

    "return false" when {
      "the channel is closed" in {
        val socketChannel = mock[SocketChannel]
        when(socketChannel.isOpen).thenReturn(false)
        val channel = new NioTcpPlaintextChannel(socketChannel, remoteAddress)
        assert(!channel.isOpen)
      }
    }
  }
}
