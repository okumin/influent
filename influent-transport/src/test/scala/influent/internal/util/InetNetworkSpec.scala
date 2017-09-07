package influent.internal.util

import org.scalatest.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.WordSpec

class InetNetworkSpec extends WordSpec {
  "getBySpec" should {
    "return Inet4Network instance" when {
      "IPv4 network" in {
        val network = InetNetwork.getBySpec("192.168.1.0/24")
        assert(network.isInstanceOf[Inet4Network])
      }
    }

    "return Inet6Network instance" when {
      "IPv6 network" in {
        val network = InetNetwork.getBySpec("2001::/48")
        assert(network.isInstanceOf[Inet6Network])
      }
    }

    "throw exception" when {
      val data = Table(
        ("address"),
        ("2001::1:::0/48"),
        ("192.168.1.1.0/20"),
        ("192.168.1.0/xx"),
        ("192.168.1.1"),
        ("2001::1")
      )
      forAll(data) { (address) =>
        s"given invalid network $address" in {
          intercept[IllegalArgumentException] { InetNetwork.getBySpec(address) }
        }
      }
    }
  }

}
