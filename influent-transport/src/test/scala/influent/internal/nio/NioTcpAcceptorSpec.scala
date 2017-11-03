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
import java.nio.channels.{SelectionKey, ServerSocketChannel, SocketChannel}
import java.util.function.{BiConsumer, Consumer}

import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioTcpAcceptorSpec extends WordSpec with MockitoSugar {
  private[this] val localAddress = new InetSocketAddress(24224)
  private[this] val nopCallback = new BiConsumer[SelectionKey, SocketChannel] {
    override def accept(k: SelectionKey, t: SocketChannel): Unit = ()
  }

  "onAcceptable" should {
    "accept a new connection" in {
      val serverSocketChannel = mock[ServerSocketChannel]
      val channel1 = mock[SocketChannel]
      val channel2 = mock[SocketChannel]
      val key = mock[SelectionKey]

      when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
      val callback = mock[BiConsumer[SelectionKey, SocketChannel]]

      val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
      assert(acceptor.onAcceptable(key) === ())

      verify(serverSocketChannel, times(3)).accept()
      verify(callback).accept(key, channel1)
      verify(callback).accept(key, channel2)
      verifyNoMoreInteractions(callback)
    }

    "not fail" when {
      "it fails accepting" in {
        val serverSocketChannel = mock[ServerSocketChannel]
        val channel = mock[SocketChannel]
        val key = mock[SelectionKey]
        when(serverSocketChannel.accept()).thenThrow(new IOException()).thenReturn(channel, null)
        val callback = mock[BiConsumer[SelectionKey, SocketChannel]]

        val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
        assert(acceptor.onAcceptable(key) === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(key, channel)
        verifyNoMoreInteractions(callback)
      }

      "the callback function fails" in {
        val serverSocketChannel = mock[ServerSocketChannel]
        val channel1 = mock[SocketChannel]
        val channel2 = mock[SocketChannel]
        val key = mock[SelectionKey]
        when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
        val callback = mock[BiConsumer[SelectionKey, SocketChannel]]
        when(callback.accept(key, channel1)).thenThrow(new RuntimeException)

        val acceptor = new NioTcpAcceptor(localAddress, callback, serverSocketChannel)
        assert(acceptor.onAcceptable(key) === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(key, channel1)
        verify(callback).accept(key, channel2)
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
