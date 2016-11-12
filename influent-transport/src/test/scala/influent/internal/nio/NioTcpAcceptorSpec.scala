package influent.internal.nio

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, ServerSocketChannel, SocketChannel}
import java.util.function.Consumer
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioTcpAcceptorSpec extends WordSpec with MockitoSugar {
  private[this] val localAddress = new InetSocketAddress(24224)
  private[this] val nopCallback = new Consumer[SocketChannel] {
    override def accept(t: SocketChannel): Unit = ()
  }

  "onAcceptable" should {
    "accept a new connection" in {
      val serverSocketChannel = mock[ServerSocketChannel]
      val channel1 = mock[SocketChannel]
      val channel2 = mock[SocketChannel]
      when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
      val callback = mock[Consumer[SocketChannel]]

      val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
      assert(acceptor.onAcceptable(mock[SelectionKey]) === ())

      verify(serverSocketChannel, times(3)).accept()
      verify(callback).accept(channel1)
      verify(callback).accept(channel2)
      verifyNoMoreInteractions(callback)
    }

    "not fail" when {
      "it fails accepting" in {
        val serverSocketChannel = mock[ServerSocketChannel]
        val channel = mock[SocketChannel]
        when(serverSocketChannel.accept()).thenThrow(new IOException()).thenReturn(channel, null)
        val callback = mock[Consumer[SocketChannel]]

        val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
        assert(acceptor.onAcceptable(mock[SelectionKey]) === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(channel)
        verifyNoMoreInteractions(callback)
      }

      "the callback function fails" in {
        val serverSocketChannel = mock[ServerSocketChannel]
        val channel1 = mock[SocketChannel]
        val channel2 = mock[SocketChannel]
        when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
        val callback = mock[Consumer[SocketChannel]]
        when(callback.accept(channel1)).thenThrow(new RuntimeException)

        val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
        assert(acceptor.onAcceptable(mock[SelectionKey]) === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(channel1)
        verify(callback).accept(channel2)
        verifyNoMoreInteractions(callback)
      }
    }
  }

  "close" should {
    "close the server socket channel" in {
      val serverSocketChannel = mock[ServerSocketChannel]
      val acceptor = new NioTcpAcceptor(localAddress, nopCallback, serverSocketChannel)
      assert(acceptor.close() === ())
      verify(serverSocketChannel).close()
    }

    "not fail" when {
      "it fails closing the server socket channel" in {
        val serverSocketChannel = mock[ServerSocketChannel]
        when(serverSocketChannel.close()).thenThrow(new IOException())

        val acceptor = new NioTcpAcceptor(localAddress, nopCallback, serverSocketChannel)
        assert(acceptor.close() === ())
        verify(serverSocketChannel).close()
      }
    }
  }
}
