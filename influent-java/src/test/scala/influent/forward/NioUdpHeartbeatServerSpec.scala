package influent.forward

import influent.exception.InfluentIOException
import influent.internal.nio.{NioEventLoop, NioUdpChannel}
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.util.Optional
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
      val eventLoop = mock[NioEventLoop]
      val channel = mock[NioUdpChannel]

      val server = new NioUdpHeartbeatServer(channel, eventLoop)
      val targets = Seq(
        new InetSocketAddress(8001),
        new InetSocketAddress(8002),
        new InetSocketAddress(8003)
      )
      targets.foreach { target =>
        server.replyTo.enqueue(target)
        when(channel.send(response, target)).thenReturn(true)
      }

      val key = mock[SelectionKey]
      assert(server.onWritable(key) === ())

      targets.foreach { target =>
        verify(channel).send(response, target)
      }
      verify(eventLoop).disableInterestSet(key, SelectionKey.OP_WRITE)
      verifyNoMoreInteractions(eventLoop)
    }

    "not disable OP_WRITE" when {
      "all responses are not flushed" in {
        val eventLoop = mock[NioEventLoop]
        val channel = mock[NioUdpChannel]

        val server = new NioUdpHeartbeatServer(channel, eventLoop)
        val targets = Seq(
          new InetSocketAddress(8001),
          new InetSocketAddress(8002),
          new InetSocketAddress(8003)
        )
        targets.foreach(server.replyTo.enqueue)
        when(channel.send(response, targets(0))).thenReturn(true)
        when(channel.send(response, targets(1))).thenReturn(false)

        val key = mock[SelectionKey]
        assert(server.onWritable(key) === ())

        verify(channel).send(response, targets(0))
        verify(channel).send(response, targets(1))
        verifyNoMoreInteractions(channel)
        verifyZeroInteractions(eventLoop)
      }
    }

    "not fail" when {
      "some IO error occurs" in {
        val eventLoop = mock[NioEventLoop]
        val channel = mock[NioUdpChannel]

        val server = new NioUdpHeartbeatServer(channel, eventLoop)
        val targets = Seq(
          new InetSocketAddress(8001),
          new InetSocketAddress(8002),
          new InetSocketAddress(8003)
        )
        targets.foreach(server.replyTo.enqueue)
        when(channel.send(response, targets(0))).thenReturn(true)
        when(channel.send(response, targets(1)))
          .thenThrow(new InfluentIOException()).thenReturn(true)
        when(channel.send(response, targets(2))).thenReturn(true)

        val key = mock[SelectionKey]
        assert(server.onWritable(key) === ())

        verify(channel).send(response, targets(0))
        verify(channel, times(2)).send(response, targets(1))
        verify(channel).send(response, targets(2))
        verifyNoMoreInteractions(channel)
      }
    }
  }

  "onReadable" should {
    "receives heartbeat requests" in {
      val eventLoop = mock[NioEventLoop]
      val channel = mock[NioUdpChannel]

      val server = new NioUdpHeartbeatServer(channel, eventLoop)
      val source1 = new InetSocketAddress(8001)
      val source2 = new InetSocketAddress(8002)
      when(channel.receive(ByteBuffer.allocate(1)))
        .thenReturn(Optional.of(source1), Optional.of(source2), Optional.empty())

      val key = mock[SelectionKey]
      assert(server.onReadable(key) === ())

      verify(channel, times(3)).receive(ByteBuffer.allocate(1))
      assert(server.replyTo.dequeue() === source1)
      assert(server.replyTo.dequeue() === source2)
      assert(!server.replyTo.nonEmpty())
      verify(eventLoop).enableInterestSet(key, SelectionKey.OP_WRITE)
      verifyNoMoreInteractions(eventLoop)
    }

    "not fail" when {
      "some IO error occurs" in {
        val eventLoop = mock[NioEventLoop]
        val channel = mock[NioUdpChannel]

        val server = new NioUdpHeartbeatServer(channel, eventLoop)
        val source: SocketAddress = new InetSocketAddress(8000)
        when(channel.receive(ByteBuffer.allocate(1)))
          .thenThrow(new InfluentIOException())
          .thenReturn(Optional.of(source), Optional.empty())

        assert(server.onReadable(mock[SelectionKey]) === ())
        verify(channel, times(3)).receive(ByteBuffer.allocate(1))
        assert(server.replyTo.dequeue() === source)
        assert(!server.replyTo.nonEmpty())
      }
    }
  }

  "close" should {
    "close the channel and inform the supervisor" in {
      val eventLoop = mock[NioEventLoop]
      val channel = mock[NioUdpChannel]

      val server = new NioUdpHeartbeatServer(channel, eventLoop)
      assert(server.close() === ())
      verify(channel).close()
    }
  }
}
