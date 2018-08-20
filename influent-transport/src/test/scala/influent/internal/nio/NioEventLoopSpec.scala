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
import java.nio.channels.{SelectableChannel, SelectionKey, Selector}
import java.util

import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioEventLoopSpec extends WordSpec with MockitoSugar {
  private[this] def withEventLoop(loop: NioEventLoop)(f: NioEventLoop => Unit): Unit = {
    val thread = new Thread(loop)
    thread.setUncaughtExceptionHandler((_: Thread, e: Throwable) => e.printStackTrace())
    thread.start()
    f(loop)
    loop.shutdown().get()
  }

  "run" should {
    "continue selecting and clean up on its complete" in {
      val selector = mock[Selector]
      when(selector.select()).thenReturn(0).thenThrow(new IOException)

      val attachment1 = mock[NioAttachment]
      val key1 = mock[SelectionKey]
      when(key1.attachment()).thenReturn(attachment1, Nil: _*)
      val attachment2 = mock[NioAttachment]
      val key2 = mock[SelectionKey]
      when(key2.attachment()).thenReturn(attachment2, Nil: _*)
      val keys = new util.LinkedHashSet[SelectionKey]()
      keys.add(key1)
      keys.add(key2)

      when(selector.keys()).thenReturn(keys)
      val loop = new NioEventLoop(selector)
      new Thread(loop).start()
      Thread.sleep(100)
      loop.shutdown().get()

      verify(selector, atLeastOnce()).select()
      verify(selector).keys()
      verify(attachment1).close()
      verify(attachment2).close()
      verify(selector).close()
    }

    "fail with IllegalStateException" when {
      "it has already started" in {
        val loop = NioEventLoop.open()
        new Thread(loop).start()
        // Prevent `loop.run()` from overtaking the above thread
        Thread.sleep(1000)

        assertThrows[IllegalStateException](loop.run())
        loop.shutdown().get()
      }
    }
  }

  "register" should {
    "add a Register task" in {
      val selector = Selector.open()
      withEventLoop(new NioEventLoop(selector)) { loop =>
        val ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE
        val attachment = new NioAttachment {
          override protected def close(): Unit = ()
        }
        val javaKey = mock[SelectionKey]
        val channel = mock[SelectableChannel]
        when(channel.configureBlocking(false)).thenReturn(channel)
        when(channel.register(selector, ops, attachment)).thenReturn(javaKey)
        val key = mock[NioSelectionKey]
        assert(loop.register(channel, key, ops, attachment) === ())

        Thread.sleep(1000)
        verify(channel).configureBlocking(false)
        verify(channel).register(selector, ops, attachment)
        verify(key).bind(javaKey)
      }
    }
  }

  "enableInterestSet" should {
    "add an UpdateInterestSet task" in {
      withEventLoop(NioEventLoop.open()) { loop =>
        val javaKey = mock[SelectionKey]
        when(javaKey.interestOps()).thenReturn(SelectionKey.OP_READ)
        val key = NioSelectionKey.create()
        key.bind(javaKey)

        assert(loop.enableInterestSet(key, SelectionKey.OP_WRITE) === ())

        Thread.sleep(1000)
        verify(javaKey).interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE)
      }
    }
  }

  "disableInterestSet" should {
    "add an UpdateInterestSet task" in {
      withEventLoop(NioEventLoop.open()) { loop =>
        val javaKey = mock[SelectionKey]
        when(javaKey.interestOps()).thenReturn(SelectionKey.OP_READ | SelectionKey.OP_WRITE)
        val key = NioSelectionKey.create()
        key.bind(javaKey)

        assert(loop.disableInterestSet(key, SelectionKey.OP_WRITE) === ())

        Thread.sleep(1000)
        verify(javaKey).interestOps(SelectionKey.OP_READ)
      }
    }
  }

  "shutdown" should {
    "clean attachments up and complete the Future" in {
      val selector = Selector.open()
      val loop = new NioEventLoop(selector)
      new Thread(loop).start()

      Thread.sleep(1000)
      val actual = loop.shutdown()
      actual.get()
      assert(!selector.isOpen)
    }

    "do nothing" when {
      "the event loop is not active" in {
        val loop = NioEventLoop.open()
        new Thread(loop).start()

        Thread.sleep(1000)
        loop.shutdown().get()
        loop.shutdown().get()
      }
    }

    "fail with IllegalStateException" when {
      "the event loop has not been started yet" in {
        val loop = NioEventLoop.open()
        assertThrows[IllegalStateException](loop.shutdown())
      }
    }
  }
}
