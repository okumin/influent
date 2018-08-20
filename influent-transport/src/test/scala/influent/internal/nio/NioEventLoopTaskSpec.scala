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
import java.nio.channels._
import java.util
import java.util.function.IntUnaryOperator

import influent.exception.InfluentIOException
import influent.internal.nio.NioEventLoopTask.UpdateInterestSet
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioEventLoopTaskSpec extends WordSpec with MockitoSugar {
  private[this] class NopAttachment extends NioAttachment {
    override def close(): Unit = ()
  }

  "Register" should {
    "register a channel" in {
      val selector = Selector.open()
      val ops = SelectionKey.OP_WRITE
      val attachment = new NopAttachment
      val javaKey = mock[SelectionKey]

      val channel = mock[SelectableChannel]
      when(channel.configureBlocking(false)).thenReturn(channel)
      when(channel.register(selector, ops, attachment)).thenReturn(javaKey)
      val key = mock[NioSelectionKey]

      val task = NioEventLoopTask.Register.of(selector, channel, key, ops, attachment)
      task.run()

      verify(channel).configureBlocking(false)
      verify(channel).register(selector, ops, attachment)
      verify(key).bind(javaKey)
    }

    "ignore errors" when {
      "it fails configuring blocking mode" in {
        val errors = Seq(
          new ClosedChannelException,
          new IOException()
        )

        errors.foreach { error =>
          val selector = Selector.open()
          val ops = SelectionKey.OP_WRITE
          val attachment = new NopAttachment

          val channel = mock[SelectableChannel]
          when(channel.configureBlocking(false)).thenThrow(error)
          val key = mock[NioSelectionKey]

          val task = NioEventLoopTask.Register.of(selector, channel, key, ops, attachment)
          task.run()

          verify(channel).configureBlocking(false)
          verify(channel, never()).register(any(), anyInt(), any())
          verify(key, never()).bind(any())
        }
      }

      "it fails registering the selector" in {
        val errors = Seq(
          new CancelledKeyException,
          new IllegalArgumentException
        )

        errors.foreach { error =>
          val selector = Selector.open()
          val ops = SelectionKey.OP_WRITE
          val attachment = new NopAttachment

          val channel = mock[SelectableChannel]
          when(channel.configureBlocking(false)).thenReturn(channel)
          when(channel.register(selector, ops, attachment)).thenThrow(error)
          val key = mock[NioSelectionKey]

          val task = NioEventLoopTask.Register.of(selector, channel, key, ops, attachment)
          task.run()

          verify(channel).configureBlocking(false)
          verify(channel).register(selector, ops, attachment)
          verify(key, never()).bind(any())
        }
      }
    }
  }

  "UpdateInterestSet" should {
    def updater = new IntUnaryOperator {
      override def applyAsInt(operand: Int): Int = operand | 2
    }

    "update an interest set" in {
      val javaKey = mock[SelectionKey]
      when(javaKey.interestOps()).thenReturn(1)
      val key = NioSelectionKey.create()
      key.bind(javaKey)

      val task = UpdateInterestSet.of(key, updater)
      task.run()

      verify(javaKey).interestOps(1 | 2)
    }

    "do nothing" when {
      "updated ops equals to the current ops" in {
        val javaKey = mock[SelectionKey]
        when(javaKey.interestOps()).thenReturn(1 | 2)
        val key = NioSelectionKey.create()
        key.bind(javaKey)

        val task = UpdateInterestSet.of(key, updater)
        task.run()

        verify(javaKey, never()).interestOps(anyInt())
      }
    }

    "ignore the error" when {
      "it fails retrieving the interest set" in {
        val javaKey = mock[SelectionKey]
        when(javaKey.interestOps()).thenThrow(new CancelledKeyException)
        val key = NioSelectionKey.create()
        key.bind(javaKey)

        val task = UpdateInterestSet.of(key, updater)
        task.run()

        verify(javaKey, never()).interestOps(anyInt())
      }

      "it fails configuring the interest set" in {
        val errors = Seq(
          new IllegalArgumentException,
          new CancelledKeyException
        )

        errors.foreach { error =>
          val javaKey = mock[SelectionKey]
          when(javaKey.interestOps()).thenReturn(1)
          when(javaKey.interestOps(1)).thenThrow(error)
          val key = NioSelectionKey.create()
          key.bind(javaKey)

          val task = UpdateInterestSet.of(key, updater)
          task.run()

          verify(javaKey).interestOps(3)
        }
      }
    }
  }

  "Select" should {
    "select and execute IO operations" in {
      val attachment = mock[NioAttachment]

      val key1 = mock[SelectionKey]
      when(key1.attachment()).thenReturn(attachment, Nil: _*)
      when(key1.isWritable).thenReturn(true)
      when(key1.isReadable).thenReturn(false)
      when(key1.isAcceptable).thenReturn(false)
      when(key1.isConnectable).thenReturn(false)

      val key2 = mock[SelectionKey]
      when(key2.attachment()).thenReturn(attachment, Nil: _*)
      when(key2.isWritable).thenReturn(false)
      when(key2.isReadable).thenReturn(true)
      when(key2.isAcceptable).thenReturn(false)
      when(key2.isConnectable).thenReturn(false)

      val key3 = mock[SelectionKey]
      when(key3.attachment()).thenReturn(attachment, Nil: _*)
      when(key3.isWritable).thenReturn(false)
      when(key3.isReadable).thenReturn(false)
      when(key3.isAcceptable).thenReturn(true)
      when(key3.isConnectable).thenReturn(false)

      val key4 = mock[SelectionKey]
      when(key4.attachment()).thenReturn(attachment, Nil: _*)
      when(key4.isWritable).thenReturn(false)
      when(key4.isReadable).thenReturn(false)
      when(key4.isAcceptable).thenReturn(false)
      when(key4.isConnectable).thenReturn(true)

      val key5 = mock[SelectionKey]
      when(key5.attachment()).thenReturn(attachment, Nil: _*)
      when(key5.isWritable).thenReturn(true)
      when(key5.isReadable).thenReturn(true)
      when(key5.isAcceptable).thenReturn(false)
      when(key5.isConnectable).thenReturn(false)

      val keys = new util.LinkedHashSet[SelectionKey]()
      keys.add(key1)
      keys.add(key2)
      keys.add(key3)
      keys.add(key4)
      keys.add(key5)

      val selector = mock[Selector]
      when(selector.select()).thenReturn(5)
      when(selector.selectedKeys()).thenReturn(keys)

      val task = NioEventLoopTask.Select.of(selector)
      task.run()

      verify(attachment, times(2)).onWritable()
      verify(attachment, times(2)).onReadable()
      verify(attachment).onAcceptable()
      verify(attachment).onConnectable()
      verifyNoMoreInteractions(attachment)
      assert(keys.size() === 0)
    }

    "do nothing" when {
      "select returns 0" in {
        val selector = mock[Selector]
        when(selector.select()).thenReturn(0)

        val task = NioEventLoopTask.Select.of(selector)
        task.run()

        verify(selector).select()
        verifyNoMoreInteractions(selector)
      }

      "select fails" in {
        val selector = mock[Selector]
        when(selector.select()).thenThrow(new IOException())

        val task = NioEventLoopTask.Select.of(selector)
        task.run()

        verify(selector).select()
        verifyNoMoreInteractions(selector)
      }
    }

    "ignore attachment errors" in {
      val attachment = mock[NioAttachment]

      val key1 = mock[SelectionKey]
      when(key1.attachment()).thenReturn(attachment, Nil: _*)
      when(key1.isWritable).thenReturn(true)
      when(key1.isReadable).thenReturn(false)
      when(key1.isAcceptable).thenReturn(false)
      when(key1.isConnectable).thenReturn(false)
      when(attachment.onWritable()).thenThrow(new InfluentIOException())

      val key2 = mock[SelectionKey]
      when(key2.attachment()).thenReturn(attachment, Nil: _*)
      when(key2.isWritable).thenReturn(false)
      when(key2.isReadable).thenReturn(true)
      when(key2.isAcceptable).thenReturn(false)
      when(key2.isConnectable).thenReturn(false)

      val keys = new util.LinkedHashSet[SelectionKey]()
      keys.add(key1)
      keys.add(key2)

      val selector = mock[Selector]
      when(selector.select()).thenReturn(2)
      when(selector.selectedKeys()).thenReturn(keys)

      val task = NioEventLoopTask.Select.of(selector)
      task.run()

      verify(attachment).onWritable()
      verify(attachment).close()
      verify(attachment).onReadable()
      verifyNoMoreInteractions(attachment)
      assert(keys.size() === 0)
    }
  }
}
