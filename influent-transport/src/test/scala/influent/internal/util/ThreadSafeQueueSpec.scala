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

package influent.internal.util

import org.scalatest.WordSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ThreadSafeQueueSpec extends WordSpec with GeneratorDrivenPropertyChecks {
  "ThreadSafeQueue" should {
    "behave like a FIFO queue" in {
      forAll { messages: Seq[Int] =>
        val queue = new ThreadSafeQueue[Int]()
        assert(queue.isEmpty)
        assert(!queue.nonEmpty())
        assert(queue.dequeue() === null)

        messages.foreach { m =>
          queue.enqueue(m)
          assert(!queue.isEmpty)
          assert(queue.nonEmpty())
        }

        whenever(messages.nonEmpty) {
          assert(queue.peek() === messages.head)
          assert(!queue.isEmpty)
          assert(queue.nonEmpty())
        }

        val elements = (1 to messages.size).foldLeft(Vector.empty[Int]) { (acc, _) =>
          acc :+ queue.dequeue()
        }
        assert(elements === messages)

        assert(queue.isEmpty)
        assert(!queue.nonEmpty())
        assert(queue.dequeue() === null)
      }
    }
  }
}
