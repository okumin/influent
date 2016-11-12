package influent.internal.util

import java.util.concurrent.CompletableFuture
import org.scalatest.Matchers._
import org.scalatest.WordSpec

class FuturesSpec extends WordSpec {
  private[this] def assertNotCompleted(future: CompletableFuture[Int]): Unit = {
    assert(!future.isDone)
    assert(!future.isCompletedExceptionally)
    assert(!future.isCancelled)
  }
  private[this] def assertSuccessful(future: CompletableFuture[Int], value: Int): Unit = {
    assert(future.isDone)
    assert(!future.isCompletedExceptionally)
    assert(!future.isCancelled)
    assert(future.get() === value)
  }
  private[this] def assertFailure(future: CompletableFuture[Int]): Unit = {
    assert(future.isDone)
    assert(future.isCompletedExceptionally)
    assert(!future.isCancelled)
  }
  private[this] def assertCancelled(future: CompletableFuture[Int]): Unit = {
    assert(future.isDone)
    assert(future.isCompletedExceptionally)
    assert(future.isCancelled)
  }

  "followerOf" should {
    "return a future that is completed" when {
      "the given future is succeeded" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        original.complete(1)
        assertSuccessful(original, 1)
        assertSuccessful(follower, 1)
      }

      "the given future is failed" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        original.completeExceptionally(new RuntimeException)
        assertFailure(original)
        assertFailure(follower)
      }

      "the given future is cancelled" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        original.cancel(true)
        assertCancelled(original)
        // Cancellations are not propagated
        assertFailure(follower)
      }
    }

    "not complete the given future" when {
      "the returned future is completed" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        follower.complete(1)
        assertNotCompleted(original)
        assertSuccessful(follower, 1)

        original.complete(2)
        assertSuccessful(original, 2)
        assertSuccessful(follower, 1)
      }

      "the returned future is completed exceptionally" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        follower.completeExceptionally(new RuntimeException)
        assertNotCompleted(original)
        assertFailure(follower)

        original.complete(1)
        assertSuccessful(original, 1)
        assertFailure(follower)
      }

      "the returned future is cancelled" in {
        val original = new CompletableFuture[Int]()
        val follower = Futures.followerOf(original)
        assertNotCompleted(original)
        assertNotCompleted(follower)

        follower.cancel(true)
        assertNotCompleted(original)
        assertCancelled(follower)

        original.complete(1)
        assertSuccessful(original, 1)
        assertCancelled(follower)
      }
    }
  }
}
