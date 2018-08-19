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

import java.nio.channels.SocketChannel
import java.util.function.Consumer

import influent.exception.InfluentIOException
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioTcpAcceptorSpec extends WordSpec with MockitoSugar {
  private[this] val nopCallback = new Consumer[SocketChannel] {
    override def accept(t: SocketChannel): Unit = ()
  }

  "onAcceptable" should {
    "accept a new connection" in {
      val serverSocketChannel = mock[NioServerSocketChannel]
      val channel1 = mock[SocketChannel]
      val channel2 = mock[SocketChannel]

      when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
      val callback = mock[Consumer[SocketChannel]]

      val acceptor = new NioTcpAcceptor(serverSocketChannel, callback)
      assert(acceptor.onAcceptable() === ())

      verify(serverSocketChannel, times(3)).accept()
      verify(callback).accept(channel1)
      verify(callback).accept(channel2)
      verifyNoMoreInteractions(callback)
    }

    "not fail" when {
      "it fails accepting" in {
        val serverSocketChannel = mock[NioServerSocketChannel]
        val channel = mock[SocketChannel]
        when(serverSocketChannel.accept())
          .thenThrow(new InfluentIOException())
          .thenReturn(channel, null)
        val callback = mock[Consumer[SocketChannel]]

        val acceptor = new NioTcpAcceptor(serverSocketChannel, callback)
        assert(acceptor.onAcceptable() === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(channel)
        verifyNoMoreInteractions(callback)
      }

      "the callback function fails" in {
        val serverSocketChannel = mock[NioServerSocketChannel]
        val channel1 = mock[SocketChannel]
        val channel2 = mock[SocketChannel]
        when(serverSocketChannel.accept()).thenReturn(channel1, channel2, null)
        val callback = mock[Consumer[SocketChannel]]
        when(callback.accept(channel1)).thenThrow(new RuntimeException)

        val acceptor = new NioTcpAcceptor(serverSocketChannel, callback)
        assert(acceptor.onAcceptable() === ())

        verify(serverSocketChannel, times(3)).accept()
        verify(callback).accept(channel1)
        verify(callback).accept(channel2)
        verifyNoMoreInteractions(callback)
      }
    }
  }

  "close" should {
    "close the server socket channel" in {
      val serverSocketChannel = mock[NioServerSocketChannel]
      val acceptor = new NioTcpAcceptor(serverSocketChannel, nopCallback)
      assert(acceptor.close() === ())
      verify(serverSocketChannel).close()
    }
  }
}
