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

package influent.internal.msgpack;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.msgpack.core.MessagePack;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.ValueFactory;

abstract class MsgpackIncrementalUnpacker {
  abstract DecodeResult unpack(final InfluentByteBuffer buffer);
}

final class FormatUnpacker extends MsgpackIncrementalUnpacker {
  @FunctionalInterface
  private interface UnpackerFactory {
    DecodeResult apply(final InfluentByteBuffer buffer);
  }

  private static int indexOf(final byte header) {
    return header & 0xff;
  }

  private static final UnpackerFactory[] UNPACKER_TABLE = new UnpackerFactory[256];

  private static void updateUnpackerTable(final byte header, final UnpackerFactory factory) {
    assert UNPACKER_TABLE[indexOf(header)] == null;
    UNPACKER_TABLE[indexOf(header)] = factory;
  }

  static {
    updateUnpackerTable(
        MessagePack.Code.NEVER_USED,
        buffer -> {
          throw new IllegalArgumentException("The first byte of message pack object is invalid.");
        });
    updateUnpackerTable(
        MessagePack.Code.NIL, buffer -> DecodeResult.complete(ValueFactory.newNil()));
    updateUnpackerTable(
        MessagePack.Code.FALSE, buffer -> DecodeResult.complete(ValueFactory.newBoolean(false)));
    updateUnpackerTable(
        MessagePack.Code.TRUE, buffer -> DecodeResult.complete(ValueFactory.newBoolean(true)));
    updateUnpackerTable(
        MessagePack.Code.BIN8,
        buffer -> SizeUnpacker.int8(ConstantUnpacker::binary).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.BIN16,
        buffer -> SizeUnpacker.int16(ConstantUnpacker::binary).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.BIN32,
        buffer -> SizeUnpacker.int32(ConstantUnpacker::binary).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FLOAT32,
        buffer ->
            new ConstantUnpacker(Float.BYTES, bytes -> ValueFactory.newFloat(bytes.getFloat()))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FLOAT64,
        buffer ->
            new ConstantUnpacker(Double.BYTES, bytes -> ValueFactory.newFloat(bytes.getDouble()))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.UINT8,
        buffer ->
            new ConstantUnpacker(1, bytes -> ValueFactory.newInteger(bytes.get() & 0xff))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.UINT16,
        buffer ->
            new ConstantUnpacker(2, bytes -> ValueFactory.newInteger(bytes.getShort() & 0xffff))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.UINT32,
        buffer ->
            new ConstantUnpacker(
                    4,
                    bytes -> {
                      final int intValue = bytes.getInt();
                      final long value =
                          intValue < 0 ? (intValue & 0x7fffffff) + 0x80000000L : intValue;
                      return ValueFactory.newInteger(value);
                    })
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.UINT64,
        buffer ->
            new ConstantUnpacker(
                    8,
                    bytes -> {
                      final long longValue = bytes.getLong();
                      if (longValue < 0L) {
                        final BigInteger value =
                            BigInteger.valueOf(longValue + Long.MAX_VALUE + 1L).setBit(63);
                        return ValueFactory.newInteger(value);
                      } else {
                        return ValueFactory.newInteger(longValue);
                      }
                    })
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.INT8,
        buffer ->
            new ConstantUnpacker(1, bytes -> ValueFactory.newInteger(bytes.get())).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.INT16,
        buffer ->
            new ConstantUnpacker(2, bytes -> ValueFactory.newInteger(bytes.getShort()))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.INT32,
        buffer ->
            new ConstantUnpacker(4, bytes -> ValueFactory.newInteger(bytes.getInt()))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.INT64,
        buffer ->
            new ConstantUnpacker(8, bytes -> ValueFactory.newInteger(bytes.getLong()))
                .unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.STR8,
        buffer -> SizeUnpacker.int8(ConstantUnpacker::string).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.STR16,
        buffer -> SizeUnpacker.int16(ConstantUnpacker::string).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.STR32,
        buffer -> SizeUnpacker.int32(ConstantUnpacker::string).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.ARRAY16,
        buffer -> SizeUnpacker.int16(MultipleUnpacker::array).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.ARRAY32,
        buffer -> SizeUnpacker.int32(MultipleUnpacker::array).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.MAP16, buffer -> SizeUnpacker.int16(MultipleUnpacker::map).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.MAP32, buffer -> SizeUnpacker.int32(MultipleUnpacker::map).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.EXT8,
        buffer -> SizeUnpacker.int8(ConstantUnpacker::extension).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.EXT16,
        buffer -> SizeUnpacker.int16(ConstantUnpacker::extension).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.EXT32,
        buffer -> SizeUnpacker.int32(ConstantUnpacker::extension).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FIXEXT1, buffer -> ConstantUnpacker.extension(1).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FIXEXT2, buffer -> ConstantUnpacker.extension(2).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FIXEXT4, buffer -> ConstantUnpacker.extension(4).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FIXEXT8, buffer -> ConstantUnpacker.extension(8).unpack(buffer));
    updateUnpackerTable(
        MessagePack.Code.FIXEXT16, buffer -> ConstantUnpacker.extension(16).unpack(buffer));

    byte i = Byte.MIN_VALUE;
    while (true) {
      final byte header = i;
      if (MessagePack.Code.isFixInt(header)) {
        // positive fixint or negative fixint
        updateUnpackerTable(
            header, buffer -> DecodeResult.complete(ValueFactory.newInteger(header)));
      }
      if (MessagePack.Code.isFixedMap(header)) {
        updateUnpackerTable(header, buffer -> MultipleUnpacker.map(header & 0x0f).unpack(buffer));
      }
      if (MessagePack.Code.isFixedArray(header)) {
        updateUnpackerTable(header, buffer -> MultipleUnpacker.array(header & 0x0f).unpack(buffer));
      }
      if (MessagePack.Code.isFixedRaw(header)) {
        updateUnpackerTable(
            header, buffer -> ConstantUnpacker.string(header & 0x1f).unpack(buffer));
      }
      assert UNPACKER_TABLE[indexOf(header)] != null;
      if (i == Byte.MAX_VALUE) {
        break;
      }
      ++i;
    }
  }

  private static final FormatUnpacker INSTANCE = new FormatUnpacker();

  private FormatUnpacker() {}

  static FormatUnpacker getInstance() {
    return INSTANCE;
  }

  @Override
  DecodeResult unpack(final InfluentByteBuffer buffer) {
    if (!buffer.hasRemaining()) {
      return DecodeResult.next(this);
    }

    final byte header = buffer.getByte();
    return UNPACKER_TABLE[indexOf(header)].apply(buffer);
  }
}

final class ConstantUnpacker extends MsgpackIncrementalUnpacker {
  private final Function<ByteBuffer, ImmutableValue> factory;
  private final ByteBuffer builder;

  ConstantUnpacker(final int size, final Function<ByteBuffer, ImmutableValue> factory) {
    this.builder = ByteBuffer.allocate(size);
    this.factory = factory;
  }

  static ConstantUnpacker binary(final int size) {
    return new ConstantUnpacker(size, bytes -> ValueFactory.newBinary(bytes.array(), true));
  }

  static ConstantUnpacker string(final int size) {
    return new ConstantUnpacker(size, bytes -> ValueFactory.newString(bytes.array(), true));
  }

  static ConstantUnpacker extension(final int size) {
    return new ConstantUnpacker(
        size + 1, // data + type
        bytes -> {
          final byte type = bytes.get();
          final byte[] data = new byte[size];
          bytes.get(data);
          return ValueFactory.newExtension(type, data);
        });
  }

  @Override
  DecodeResult unpack(final InfluentByteBuffer buffer) {
    buffer.get(builder);
    if (builder.hasRemaining()) {
      return DecodeResult.next(this);
    } else {
      builder.flip();
      return DecodeResult.complete(factory.apply(builder));
    }
  }
}

final class MultipleUnpacker extends MsgpackIncrementalUnpacker {
  private final ImmutableValue[] builder;
  private final Function<ImmutableValue[], ImmutableValue> factory;

  private int position = 0;
  private MsgpackIncrementalUnpacker current = FormatUnpacker.getInstance();

  private MultipleUnpacker(
      final int size, final Function<ImmutableValue[], ImmutableValue> factory) {
    this.builder = new ImmutableValue[size];
    this.factory = factory;
  }

  static MultipleUnpacker map(final int size) {
    return new MultipleUnpacker(size * 2, values -> ValueFactory.newMap(values, true));
  }

  static MultipleUnpacker array(final int size) {
    return new MultipleUnpacker(size, values -> ValueFactory.newArray(values, true));
  }

  @Override
  final DecodeResult unpack(final InfluentByteBuffer buffer) {
    while (position < builder.length) {
      final DecodeResult result = current.unpack(buffer);
      if (!result.isCompleted()) {
        current = result.next();
        return DecodeResult.next(this);
      }

      builder[position++] = result.value();
      current = FormatUnpacker.getInstance();
    }

    return DecodeResult.complete(factory.apply(builder));
  }
}

final class SizeUnpacker extends MsgpackIncrementalUnpacker {
  private final ByteBuffer dst;
  private final ToIntFunction<ByteBuffer> sizeConverter;
  private final IntFunction<MsgpackIncrementalUnpacker> factory;

  private static final ToIntFunction<ByteBuffer> INT8_CONVERTER = buffer -> buffer.get() & 0xff;
  private static final ToIntFunction<ByteBuffer> INT16_CONVERTER =
      buffer -> buffer.getShort() & 0xffff;
  private static final ToIntFunction<ByteBuffer> INT32_CONVERTER =
      buffer -> {
        final int size = buffer.getInt();
        if (size < 0) {
          throw new RuntimeException("The length exceeds Integer.MAX_VALUE.");
        }
        return size;
      };

  static SizeUnpacker int8(final IntFunction<MsgpackIncrementalUnpacker> factory) {
    return new SizeUnpacker(1, INT8_CONVERTER, factory);
  }

  static SizeUnpacker int16(final IntFunction<MsgpackIncrementalUnpacker> factory) {
    return new SizeUnpacker(2, INT16_CONVERTER, factory);
  }

  static SizeUnpacker int32(final IntFunction<MsgpackIncrementalUnpacker> factory) {
    return new SizeUnpacker(4, INT32_CONVERTER, factory);
  }

  private SizeUnpacker(
      final int bytes,
      final ToIntFunction<ByteBuffer> converter,
      final IntFunction<MsgpackIncrementalUnpacker> factory) {
    this.dst = ByteBuffer.allocate(bytes);
    this.sizeConverter = converter;
    this.factory = factory;
  }

  @Override
  DecodeResult unpack(final InfluentByteBuffer buffer) {
    buffer.get(dst);
    if (dst.hasRemaining()) {
      return DecodeResult.next(this);
    } else {
      dst.flip();
      final int size = sizeConverter.applyAsInt(dst);
      return factory.apply(size).unpack(buffer);
    }
  }
}
