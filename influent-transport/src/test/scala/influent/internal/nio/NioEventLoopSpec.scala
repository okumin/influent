package influent.internal.nio

import java.io.IOException
import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.{SelectableChannel, SelectionKey, Selector}
import java.util
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class NioEventLoopSpec extends WordSpec with GeneratorDrivenPropertyChecks with MockitoSugar {
  private[this] def withEventLoop(loop: NioEventLoop)(f: NioEventLoop => Unit): Unit = {
    val thread = new Thread(loop)
    thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = e.printStackTrace()
    })
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
        val channel = mock[SelectableChannel]
        when(channel.configureBlocking(false)).thenReturn(channel)
        when(channel.register(selector, ops, attachment)).thenReturn(mock[SelectionKey])
        assert(loop.register(channel, ops, attachment) === ())

        Thread.sleep(1000)
        verify(channel).configureBlocking(false)
        verify(channel).register(selector, ops, attachment)
      }
    }
  }

  "enableInterestSet" should {
    "add an UpdateInterestSet task" in {
      withEventLoop(NioEventLoop.open()) { loop =>
        val key = mock[SelectionKey]
        when(key.interestOps()).thenReturn(SelectionKey.OP_READ)

        assert(loop.enableInterestSet(key, SelectionKey.OP_WRITE) === ())

        Thread.sleep(1000)
        verify(key).interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE)
      }
    }
  }

  "disableInterestSet" should {
    "add an UpdateInterestSet task" in {
      withEventLoop(NioEventLoop.open()) { loop =>
        val key = mock[SelectionKey]
        when(key.interestOps()).thenReturn(SelectionKey.OP_READ | SelectionKey.OP_WRITE)

        assert(loop.disableInterestSet(key, SelectionKey.OP_WRITE) === ())

        Thread.sleep(1000)
        verify(key).interestOps(SelectionKey.OP_READ)
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
