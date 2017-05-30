package influent.internal.nio

import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar

class NioSingleThreadEventLoopPoolSpec extends WordSpec with MockitoSugar {
  "shutdown" should {
    "shutdown the running event loop" in {
      val eventLoop = mock[NioEventLoop]
      val pool = new NioSingleThreadEventLoopPool(eventLoop)
      pool.shutdown()
      verify(eventLoop).shutdown()
    }
  }
}
