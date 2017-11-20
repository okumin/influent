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
import java.nio.channels.{ClosedChannelException, ServerSocketChannel, SocketChannel}

import influent.exception.InfluentIOException
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class NioServerSocketChannelSpec
  extends WordSpec with GeneratorDrivenPropertyChecks with MockitoSugar {
  private[this] val localAddress = new InetSocketAddress("127.0.0.1", 24224)

  "accept" should {
    "invoke ServerSocketChannel#accept" in {
      val underlying = mock[ServerSocketChannel]
      val socketChannel = mock[SocketChannel]
      when(underlying.accept()).thenReturn(socketChannel)
      val channel = new NioServerSocketChannel(underlying, localAddress)
      val actual = channel.accept()
      assert(actual === socketChannel)
      verify(underlying).accept()
    }

    "fail with InfluentIOException" when {
      "some IO error occurs" in {
        val errors = Seq(new ClosedChannelException, new IOException)
        errors.foreach { error =>
          val underlying = mock[ServerSocketChannel]
          when(underlying.accept()).thenThrow(error)
          val channel = new NioServerSocketChannel(underlying, localAddress)
          assertThrows[InfluentIOException] {
            channel.accept()
          }
        }
      }
    }
  }

  "close" should {
    "invoke ServerSocketChannel#close" in {
      val underlying = mock[ServerSocketChannel]
      val channel = new NioServerSocketChannel(underlying, localAddress)
      assert(channel.close() === ())
      verify(underlying).close()
    }

    "not fail" when {
      "ServerSocketChannel#close fails" in {
        val underlying = mock[ServerSocketChannel]
        when(underlying.close()).thenThrow(new IOException())
        val channel = new NioServerSocketChannel(underlying, localAddress)
        assert(channel.close() === ())
        verify(underlying).close()
      }
    }
  }
}
