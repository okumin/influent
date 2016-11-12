package influent.forward

import influent._
import java.math.BigInteger
import java.time.{Clock, Instant, ZoneId}
import java.util.Optional
import org.msgpack.core.MessagePack
import org.msgpack.value.Value
import org.msgpack.value.impl._
import org.scalatest.WordSpec
import scala.collection.JavaConverters._

class MsgpackForwardRequestDecoderSpec extends WordSpec {
  private[this] def string(value: String): ImmutableStringValueImpl = {
    new ImmutableStringValueImpl(value)
  }
  private[this] def integer(value: Long): ImmutableLongValueImpl = {
    new ImmutableLongValueImpl(value)
  }
  private[this] def array(values: Value*): ImmutableArrayValueImpl = {
    new ImmutableArrayValueImpl(values.toArray)
  }
  private[this] def map(kvs: (Value, Value)*): ImmutableMapValueImpl = {
    new ImmutableMapValueImpl(kvs.flatMap { case (k, v) => Seq(k, v) }.toArray)
  }

  "decode" should {
    "parse an forward mode request" in {
      val decoder = new MsgpackForwardRequestDecoder
      val value = array(
        string("tag"),
        array(
          array(integer(100), map(string("key1") -> string("value1"))),
          array(integer(200), map(string("key2") -> string("value2")))
        )
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(
            EventEntry.of(Instant.ofEpochSecond(100), map(string("key1") -> string("value1"))),
            EventEntry.of(Instant.ofEpochSecond(200), map(string("key2") -> string("value2")))
          ).asJava
        ),
        ForwardOption.empty()
      )
      assert(actual.get() === expected)
    }

    "parse an forward mode request with option" in {
      val decoder = new MsgpackForwardRequestDecoder
      val value = array(
        string("tag"),
        array(
          array(integer(100), map(string("key1") -> string("value1"))),
          array(integer(200), map(string("key2") -> string("value2")))
        ),
        map(string("chunk") -> string("123456789"), string("unknown") -> string("mofu"))
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(
            EventEntry.of(Instant.ofEpochSecond(100), map(string("key1") -> string("value1"))),
            EventEntry.of(Instant.ofEpochSecond(200), map(string("key2") -> string("value2")))
          ).asJava
        ),
        ForwardOption.of("123456789", null)
      )
      assert(actual.get() === expected)
    }

    "parse an packed forward mode request" in {
      val decoder = new MsgpackForwardRequestDecoder
      val packer = MessagePack.newDefaultBufferPacker()
      packer.packValue(array(integer(100), map(string("key1") -> string("value1"))))
      packer.packValue(array(integer(200), map(string("key2") -> string("value2"))))
      val stream = new ImmutableStringValueImpl(packer.toByteArray)
      val value = array(
        string("tag"),
        stream
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(
            EventEntry.of(Instant.ofEpochSecond(100), map(string("key1") -> string("value1"))),
            EventEntry.of(Instant.ofEpochSecond(200), map(string("key2") -> string("value2")))
          ).asJava
        ),
        ForwardOption.empty()
      )
      assert(actual.get() === expected)
    }

    "parse an packed forward mode request with option" in {
      val decoder = new MsgpackForwardRequestDecoder
      val packer = MessagePack.newDefaultBufferPacker()
      packer.packValue(array(integer(100), map(string("key1") -> string("value1"))))
      packer.packValue(array(integer(200), map(string("key2") -> string("value2"))))
      val stream = new ImmutableStringValueImpl(packer.toByteArray)
      val value = array(
        string("tag"),
        stream,
        map(string("compressed") -> string("gzip"))
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(
            EventEntry.of(Instant.ofEpochSecond(100), map(string("key1") -> string("value1"))),
            EventEntry.of(Instant.ofEpochSecond(200), map(string("key2") -> string("value2")))
          ).asJava
        ),
        ForwardOption.of(null, "gzip")
      )
      assert(actual.get() === expected)
    }

    "parse an event mode request" in {
      val decoder = new MsgpackForwardRequestDecoder
      val value = array(
        string("tag"),
        integer(100),
        map(string("key") -> string("value"))
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(EventEntry.of(
            Instant.ofEpochSecond(100), map(string("key") -> string("value"))
          )).asJava
        ),
        ForwardOption.empty()
      )
      assert(actual.get() === expected)
    }

    "parse an event mode request with option" in {
      val decoder = new MsgpackForwardRequestDecoder
      val value = array(
        string("tag"),
        integer(100),
        map(string("key") -> string("value")),
        map(string("chunk") -> string("123456789"), string("compressed") -> string("gzip"))
      )

      val actual = decoder.decode(value)
      val expected = ForwardRequest.of(
        EventStream.of(
          Tag.of("tag"),
          Seq(EventEntry.of(
            Instant.ofEpochSecond(100), map(string("key") -> string("value"))
          )).asJava
        ),
        ForwardOption.of("123456789", "gzip")
      )
      assert(actual.get() === expected)
    }

    "return the current time" when {
      "the time is zero" in {
        val instant = Instant.ofEpochSecond(1000, 9999)
        val clock = Clock.fixed(instant, ZoneId.of("UTC"))
        val decoder = new MsgpackForwardRequestDecoder(clock)
        val value = array(
          string("tag"),
          integer(0),
          map(string("key") -> string("value"))
        )

        val actual = decoder.decode(value)
        val expected = ForwardRequest.of(
          EventStream.of(
            Tag.of("tag"),
            Seq(EventEntry.of(
              instant, map(string("key") -> string("value"))
            )).asJava
          ),
          ForwardOption.empty()
        )
        assert(actual.get() === expected)
      }
    }

    "return Optional.empty()" when {
      "the value is nil" in {
        val decoder = new MsgpackForwardRequestDecoder
        assert(decoder.decode(ImmutableNilValueImpl.get()) === Optional.empty())
      }
    }

    "fail with IllegalArgumentException" when {
      "the value is not an array" in {
        val decoder = new MsgpackForwardRequestDecoder
        assertThrows[IllegalArgumentException](decoder.decode(ImmutableMapValueImpl.empty()))
      }

      "the tag is invalid" in {
        val decoder = new MsgpackForwardRequestDecoder
        val value = array(
          integer(500),
          integer(100),
          map(string("key") -> string("value"))
        )

        assertThrows[IllegalArgumentException](decoder.decode(value))
      }

      "the time is invalid" in {
        val decoder = new MsgpackForwardRequestDecoder
        val value = array(
          string("tag"),
          new ImmutableBigIntegerValueImpl(new BigInteger("111111111111111111111")),
          map(string("key") -> string("value"))
        )

        assertThrows[IllegalArgumentException](decoder.decode(value))
      }

      "the record is invalid" in {
        val decoder = new MsgpackForwardRequestDecoder
        val value = array(
          string("tag"),
          integer(100),
          array(string("key"), string("value"))
        )

        assertThrows[IllegalArgumentException](decoder.decode(value))
      }

      "the option is invalid" in {
        val decoder = new MsgpackForwardRequestDecoder
        val value = array(
          string("tag"),
          integer(100),
          map(string("key") -> string("value")),
          array(string("chunk"), string("123456789"), string("compressed"), string("gzip"))
        )

        assertThrows[IllegalArgumentException](decoder.decode(value))
      }

      "the array has insufficient element" in {
        val testCases = Seq(
          ImmutableArrayValueImpl.empty(),
          array(string("only_tag")),
          array(
            string("without_record"),
            integer(100)
          )
        )
        val decoder = new MsgpackForwardRequestDecoder

        testCases.foreach { value =>
          assertThrows[IllegalArgumentException](decoder.decode(value))
        }
      }
    }
  }
}
