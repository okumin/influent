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

package influent.forward;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.msgpack.core.MessageIntegerOverflowException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageStringCodingException;
import org.msgpack.core.MessageTypeCastException;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.ExtensionValue;
import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.ImmutableMapValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.RawValue;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableStringValueImpl;

import influent.EventEntry;
import influent.EventStream;
import influent.Tag;

final class MsgpackForwardRequestDecoder {
  private static final Value CHUNK_KEY = new ImmutableStringValueImpl("chunk");
  private static final Value COMPRESSED_KEY = new ImmutableStringValueImpl("compressed");
  private static final int EVENT_TIME_LENGTH = 8;
  private static final ThreadLocal<ByteBuffer> EVENT_TIME_BUFFER =
      ThreadLocal.withInitial(() -> ByteBuffer.allocate(EVENT_TIME_LENGTH));

  private final Clock clock;

  MsgpackForwardRequestDecoder() {
    this(Clock.systemUTC());
  }

  MsgpackForwardRequestDecoder(final Clock clock) {
    this.clock = clock;
  }

  /**
   * Decodes a request from clients.
   *
   * <p>{@see https://github.com/fluent/fluentd/wiki/Forward-Protocol-Specification-v1}
   *
   * <p>{{{ Connection ::= <<Request>>* Request ::= Message | Forward | PackedForward | nil Message
   * ::= [ Tag, Time, Record, Option? ] Forward ::= [ Tag, MultiEventStream, Option? ]
   * MultiEventStream ::= [ Event* ] PackedForward ::= [ Tag, MessagePackEventStream, Option? ]
   * MessagePackEventStream ::= <<Event>>* Event ::= [ Time, Record ] Tag ::= string Time ::=
   * integer | EventTime Record ::= object Option ::= object }}}
   *
   * @param value msgpack value
   * @return {@code ForwardRequest} if {@code value} is a forward request, {@code Optional.empty()}
   *     if {@code value} is nil that is a future heartbeat request
   */
  Optional<ForwardRequest> decode(final ImmutableValue value) {
    if (value.isNilValue()) return Optional.empty(); // reserved

    if (!value.isArrayValue()) {
      throw error("A request must be an array.", value);
    }
    final ImmutableArrayValue message = value.asArrayValue();

    if (message.size() < 2) {
      throw error("The size of array is too small.", value);
    }

    final Tag tag = decodeTag(message.get(0));

    final Value second = message.get(1);
    final List<EventEntry> entries;
    ForwardOption option = ForwardOption.empty();
    // TODO: CompressedPackedForward
    if (second.isArrayValue()) {
      entries = decodeMultiEventStream(second.asArrayValue());
      if (message.size() > 2) {
        option = decodeOption(message.get(2));
      }
    } else if (second.isStringValue() || second.isBinaryValue()) {
      entries = decodeMessagePackEventStream(second.asRawValue());
      if (message.size() > 2) {
        option = decodeOption(message.get(2));
      }
    } else {
      if (message.size() < 3) {
        throw error("The size of array is too small.", value);
      }
      final Instant time = decodeTime(second);
      final ImmutableMapValue record = decodeRecord(message.get(2));
      entries = Collections.singletonList(EventEntry.of(time, record));
      if (message.size() > 3) {
        option = decodeOption(message.get(3));
      }
    }

    return Optional.of(ForwardRequest.of(EventStream.of(tag, entries), option));
  }

  private List<EventEntry> decodeMultiEventStream(final ArrayValue value) {
    return value.list().stream().map(this::decodeEntry).collect(Collectors.toList());
  }

  private List<EventEntry> decodeMessagePackEventStream(final RawValue value) {
    final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(value.asByteArray());
    final List<EventEntry> entries = new LinkedList<>();
    try {
      while (unpacker.hasNext()) {
        entries.add(decodeEntry(unpacker.unpackValue()));
      }
    } catch (final IOException e) {
      // An unpacker with array bytes should never fail……
      throw error("Failed unpacking.", value, e);
    }
    return entries;
  }

  private EventEntry decodeEntry(final Value value) {
    try {
      final ArrayValue entry = value.asArrayValue();
      final Instant time = decodeTime(entry.get(0));
      final ImmutableMapValue record = decodeRecord(entry.get(1));
      return EventEntry.of(time, record);
    } catch (final MessageTypeCastException e) {
      throw error("The given entry is not an array.", value, e);
    } catch (final IndexOutOfBoundsException e) {
      throw error("The given entry does not required values.", value, e);
    }
  }

  private Tag decodeTag(final Value value) {
    try {
      return Tag.of(value.asStringValue().asString());
    } catch (final MessageTypeCastException e) {
      throw error("The given tag is not a string.", value, e);
    } catch (final MessageStringCodingException e) {
      throw error("The given tag is not utf8.", value, e);
    }
  }

  private Instant decodeTime(final Value value) {
    try {
      if (value.isNilValue()) {
        return Instant.now(clock);
      }

      final Instant time;
      if (value.isExtensionValue()) {
        final ExtensionValue extValue = value.asExtensionValue();
        if (extValue.getType() != 0 || extValue.getData().length != EVENT_TIME_LENGTH) {
          throw error("The given time has invalid format.", value);
        }
        final ByteBuffer buffer = EVENT_TIME_BUFFER.get();
        buffer.clear();
        buffer.put(extValue.getData()).flip();
        final long seconds = buffer.getInt();
        final long nanoseconds = buffer.getInt();
        time = Instant.ofEpochSecond(seconds, nanoseconds);
      } else if (value.isIntegerValue()) {
        final long seconds = value.asIntegerValue().asLong();
        time = Instant.ofEpochSecond(seconds);
      } else {
        throw error("The given time has invalid format.", value);
      }

      // nanoseconds is ignored
      return time.getEpochSecond() == 0 ? Instant.now(clock) : time;
    } catch (final MessageIntegerOverflowException e) {
      throw error("The given time is out of long range.", value, e);
    } catch (final DateTimeException e) {
      throw error("The given time is invalid.", value, e);
    }
  }

  private ImmutableMapValue decodeRecord(final Value value) {
    try {
      return value.immutableValue().asMapValue();
    } catch (final MessageTypeCastException e) {
      throw error("The given record is not a map.", value, e);
    }
  }

  private ForwardOption decodeOption(final Value value) {
    try {
      final Map<Value, Value> v = value.asMapValue().map();
      final String chunk = decodeString(v.get(CHUNK_KEY));
      final String compressed = decodeString(v.get(COMPRESSED_KEY));
      return ForwardOption.of(chunk, compressed);
    } catch (final MessageTypeCastException e) {
      throw error("The given option has invalid format.", value, e);
    } catch (final MessageStringCodingException e) {
      throw error("The given chunk is invalid.", value, e);
    }
  }

  private String decodeString(final Value value) {
    return value == null ? null : value.asStringValue().asString();
  }

  private IllegalArgumentException error(final String message, final Value value) {
    throw new IllegalArgumentException(message + " value = " + value);
  }

  private IllegalArgumentException error(
      final String message, final Value value, final Throwable cause) {
    throw new IllegalArgumentException(message + " value = " + value, cause);
  }
}
