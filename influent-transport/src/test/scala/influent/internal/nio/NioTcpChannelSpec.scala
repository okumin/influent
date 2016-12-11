package influent.internal.nio

import influent.exception.InfluentIOException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{ClosedChannelException, SelectionKey, SocketChannel}
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioTcpChannelSpec extends WordSpec with MockitoSugar {
  "write" should {
    "write bytes to the channel" in {
      val src = ByteBuffer.allocate(8)
      val socketChannel = mock[SocketChannel]
      when(socketChannel.write(src)).thenReturn(4)
      val channel = new NioTcpChannel(socketChannel)

      assert(channel.write(src) === 4)
      verify(socketChannel).write(src)
      verify(socketChannel, never()).close()
    }

    "fail with InfluentIOException" when {
      "it fails writing" in {
        val src = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.write(src)).thenThrow(new IOException())
        val channel = new NioTcpChannel(socketChannel)

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
      val channel = new NioTcpChannel(socketChannel)

      assert(channel.read(dst) === 4)
      verify(socketChannel, never()).close()
    }

    "do nothing" when {
      "the socket buffer is empty" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenReturn(0)
        val channel = new NioTcpChannel(socketChannel)

        assert(channel.read(dst) === 0)
        verify(socketChannel, never()).close()
      }
    }

    "return -1" when {
      "the stream is completed" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenThrow(new ClosedChannelException())
        val channel = new NioTcpChannel(socketChannel)

        assert(channel.read(dst) === -1)
        verify(socketChannel).close()
      }
    }

    "fail with InfluentIOException" when {
      "it fails reading" in {
        val dst = ByteBuffer.allocate(8)
        val socketChannel = mock[SocketChannel]
        when(socketChannel.read(dst)).thenThrow(new IOException())
        val channel = new NioTcpChannel(socketChannel)

        assertThrows[InfluentIOException](channel.read(dst))
        verify(socketChannel).close()
      }
    }
  }

  "register" should {
    "registers this channel to the event loop" in {
      val socketChannel = mock[SocketChannel]
      val channel = new NioTcpChannel(socketChannel)

      val eventLoop = mock[NioEventLoop]
      val ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE
      val attachment = mock[NioAttachment]

      assert(channel.register(eventLoop, ops, attachment) === ())
      verify(eventLoop).register(socketChannel, ops, attachment)
    }
  }

  "close" should {
    "close the socket channel" in {
      val socketChannel = mock[SocketChannel]
      val channel = new NioTcpChannel(socketChannel)
      assert(channel.close() === ())
      verify(socketChannel).close()
    }

    "not fail" when {
      "it fails closing the socket channel" in {
        val socketChannel = mock[SocketChannel]
        when(socketChannel.close()).thenThrow(new IOException)
        val channel = new NioTcpChannel(socketChannel)
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
        val channel = new NioTcpChannel(socketChannel)
        assert(channel.isOpen)
      }
    }

    "return false" when {
      "the channel is closed" in {
        val socketChannel = mock[SocketChannel]
        when(socketChannel.isOpen).thenReturn(false)
        val channel = new NioTcpChannel(socketChannel)
        assert(!channel.isOpen)
      }
    }
  }
}
