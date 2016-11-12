package influent.internal.util

import org.scalatest.WordSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ThreadSafeQueueSpec extends WordSpec with GeneratorDrivenPropertyChecks {
  "ThreadSafeQueue" should {
    "behave like a FIFO queue" in {
      forAll { messages: Seq[Int] =>
        val queue = new ThreadSafeQueue[Int]()
        assert(!queue.nonEmpty())
        assert(queue.dequeue() === null)

        messages.foreach { m =>
          queue.enqueue(m)
          assert(queue.nonEmpty())
        }

        whenever(messages.nonEmpty) {
          assert(queue.peek() === messages.head)
          assert(queue.nonEmpty())
        }

        val elements = (1 to messages.size).foldLeft(Vector.empty[Int]) { (acc, _) =>
          acc :+ queue.dequeue()
        }
        assert(elements === messages)

        assert(!queue.nonEmpty())
        assert(queue.dequeue() === null)
      }
    }
  }
}
