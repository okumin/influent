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

import java.util.concurrent.CompletableFuture
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioRoundRobinEventLoopPoolSpec extends WordSpec with MockitoSugar {
  "next" should {
    "return an event loop in round-robin" in {
      val eventLoops = (0 until 5).map { _ => mock[NioEventLoop] }
      val pool = new NioRoundRobinEventLoopPool(eventLoops.toArray)
      val actual = (0 until 5).map { _ =>
        pool.next()
      }
      assert(actual.toSet.size === 5)
      assert(actual.toSet === eventLoops.toSet)
    }
  }

  "shutdown" should {
    "shutdown all the event loop" in {
      val eventLoops = (0 until 5).map { _ => mock[NioEventLoop] }
      val futures = eventLoops.map { eventLoop =>
        val future = new CompletableFuture[Void]()
        when(eventLoop.shutdown()).thenReturn(future)
        future
      }
      val pool = new NioRoundRobinEventLoopPool(eventLoops.toArray)
      val actual = pool.shutdown()
      eventLoops.foreach { eventLoop =>
        verify(eventLoop).shutdown()
      }

      assert(!actual.isDone)
      futures.head.complete(null)
      assert(!actual.isDone)
      futures.tail.foreach(_.complete(null))
      assert(actual.isDone)
    }
  }
}
