package influent.internal.util

import java.net.InetAddress

import org.scalatest.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.WordSpec

class Inet6NetworkSpec extends WordSpec {
  "contains" should {
    val data1 = Table(
      ("address", "isContained"),
      ("2001::1", true),
      ("2001::dead:beaf:1:1:1", true),
      ("2001:1:0::1", false)
    )
    forAll(data1) { (address, isContained) =>
      s"return $isContained" when {
        s"$address is given" in {
          val network = InetNetwork.getBySpec("2001::/48")
          assert(network.contains(InetAddress.getByName(address)) === isContained)
        }
      }
    }
  }
}
