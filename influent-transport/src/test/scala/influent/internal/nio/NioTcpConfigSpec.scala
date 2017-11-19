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

import influent.internal.nio.NioTcpConfig.Builder
import java.util.OptionalInt
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.WordSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class NioTcpConfigSpec extends WordSpec with GeneratorDrivenPropertyChecks {
  "Builder" should {
    "return the default configuration" when {
      "there is no mutation" in {
        val actual = new Builder().build()
        val expected = new NioTcpConfig(0, 0, 0, true, true)
        assert(actual === expected)
      }

      "the default values are given" in {
        val actual = new Builder()
          .backlog(0)
          .sendBufferSize(0)
          .receiveBufferSize(0)
          .keepAliveEnabled(true)
          .tcpNoDelayEnabled(true)
          .build()
        val expected = new NioTcpConfig(0, 0, 0, true, true)
        assert(actual === expected)
      }
    }

    "configure the given values" in {
      val gen: Gen[(Int, Int, Int, Boolean, Boolean)] = {
        for {
          backlog <- Gen.chooseNum(0, Int.MaxValue)
          sendBufferSize <- Gen.chooseNum(0, Int.MaxValue)
          receiveBufferSize <- Gen.chooseNum(0, Int.MaxValue)
          keepAliveEnabled <- Arbitrary.arbBool.arbitrary
          tcpNoDelayEnabled <- Arbitrary.arbBool.arbitrary
        } yield (backlog, sendBufferSize, receiveBufferSize, keepAliveEnabled, tcpNoDelayEnabled)
      }

      forAll(gen) {
        case (backlog, sendBufferSize, receiveBufferSize, keepAliveEnabled, tcpNoDelayEnabled) =>
          val actual = new Builder()
            .backlog(backlog)
            .sendBufferSize(sendBufferSize)
            .receiveBufferSize(receiveBufferSize)
            .keepAliveEnabled(keepAliveEnabled)
            .tcpNoDelayEnabled(tcpNoDelayEnabled)
            .build()
          val expected = new NioTcpConfig(
            backlog,
            sendBufferSize,
            receiveBufferSize,
            keepAliveEnabled,
            tcpNoDelayEnabled
          )
          assert(actual === expected)
      }
    }

    "fail" when {
      "the given backlog is illegal" in {
        forAll(Gen.negNum[Int]) { value =>
          assertThrows[IllegalArgumentException] {
            new Builder().backlog(value)
          }
        }
      }

      "the given sendBufferSize is illegal" in {
        forAll(Gen.negNum[Int]) { value =>
          assertThrows[IllegalArgumentException] {
            new Builder().sendBufferSize(value)
          }
        }
      }

      "the given receiveBufferSize is illegal" in {
        forAll(Gen.negNum[Int]) { value =>
          assertThrows[IllegalArgumentException] {
            new Builder().receiveBufferSize(value)
          }
        }
      }
    }
  }

  "getBacklog" should {
    "return the value" when {
      "the value > 0" in {
        forAll(Gen.posNum[Int]) { value =>
          val actual = new Builder().backlog(value).build()
          assert(actual.getBacklog === OptionalInt.of(value))
        }
      }
    }

    "return Optional.empty()" when {
      "the value == 0" in {
        val actual = new Builder().backlog(0).build()
        assert(actual.getBacklog === OptionalInt.empty())
      }
    }
  }

  "getSendBufferSize" should {
    "return the value" when {
      "the value > 0" in {
        forAll(Gen.posNum[Int]) { value =>
          val actual = new Builder().sendBufferSize(value).build()
          assert(actual.getSendBufferSize === OptionalInt.of(value))
        }
      }
    }

    "return Optional.empty()" when {
      "the value == 0" in {
        val actual = new Builder().sendBufferSize(0).build()
        assert(actual.getSendBufferSize === OptionalInt.empty())
      }
    }
  }

  "getReceiveBufferSize" should {
    "return the value" when {
      "the value > 0" in {
        forAll(Gen.posNum[Int]) { value =>
          val actual = new Builder().receiveBufferSize(value).build()
          assert(actual.getReceiveBufferSize === OptionalInt.of(value))
        }
      }
    }

    "return Optional.empty()" when {
      "the value == 0" in {
        val actual = new Builder().receiveBufferSize(0).build()
        assert(actual.getReceiveBufferSize === OptionalInt.empty())
      }
    }
  }

  "getKeepAliveEnabled" should {
    "return the value" in {
      forAll { value: Boolean =>
        val actual = new Builder().keepAliveEnabled(value).build()
        assert(actual.getKeepAliveEnabled === value)
      }
    }
  }

  "getTcpNoDelayEnabled" should {
    "return the value" in {
      forAll { value: Boolean =>
        val actual = new Builder().tcpNoDelayEnabled(value).build()
        assert(actual.getTcpNoDelayEnabled === value)
      }
    }
  }
}
