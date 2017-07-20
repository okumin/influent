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

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import influent.internal.nio.NioChannel;
import influent.internal.nio.NioSslChannel;
import org.msgpack.value.ImmutableValue;

import influent.exception.InfluentIOException;
import influent.internal.nio.NioTcpChannel;

/**
 * An unpacker for a MessagePack stream.
 *
 * This is expected to be used in only Influent project.
 *
 * {@code MsgpackStreamUnpacker} is not thread-safe.
 */
public final class MsgpackStreamUnpacker {
  private final InfluentByteBuffer buffer;
  private final long chunkSizeLimit;

  private final Queue<ImmutableValue> unpackedValues = new LinkedList<>();
  private MsgpackIncrementalUnpacker currentUnpacker = FormatUnpacker.getInstance();
  private long currentChunkSize = 0;

  /**
   * Constructs a new {@code MsgpackStreamUnpacker}.
   *
   * @param chunkSizeLimit the allowable chunk size
   *                       {@code feed} fails when the size of reading chunk exceeds the limit
   */
  public MsgpackStreamUnpacker(final long chunkSizeLimit) {
    this.buffer = new InfluentByteBuffer(chunkSizeLimit);
    this.chunkSizeLimit = chunkSizeLimit;
  }

  /**
   * Reads buffers from a {@code ReadableByteChannel}.
   *
   * @param channel channel
   * @throws InfluentIOException when it fails reading from the channel
   *                             or the chunk size exceeds the limit
   */
  public void feed(final NioChannel channel) {
    boolean toBeContinued = true;
    while (toBeContinued) {
      toBeContinued = buffer.feed(channel);
      unpack(channel);
    }
  }

  // fails when the chunk size exceeds the limit
  private void unpack(final NioChannel channel) {
    while (buffer.hasRemaining()) {
      try {
        currentChunkSize += buffer.remaining();
        final DecodeResult result = currentUnpacker.unpack(buffer);
        currentChunkSize -= buffer.remaining();
        if (result.isCompleted()) {
          unpackedValues.offer(result.value());
          currentUnpacker = FormatUnpacker.getInstance();
          currentChunkSize = 0;
        } else if (currentChunkSize >= chunkSizeLimit) {
          channel.close();
          throw new InfluentIOException("The chunk size exceeds the limit. size = "
              + buffer.remaining() + ", limit = " + chunkSizeLimit);
        } else {
          currentUnpacker = result.next();
          break;
        }
      } catch (final InfluentIOException e) {
        throw e;
      } catch (final Exception e) {
        channel.close();
        throw new InfluentIOException("Failed unpacking.", e);
      }
    }
  }

  /**
   * @return true if this {@code MsgpackStreamUnpacker} can return the next value
   */
  public boolean hasNext() {
    return !unpackedValues.isEmpty();
  }

  /**
   * @return the next {@code ImmutableValue}
   * @throws NoSuchElementException when no next value is found
   */
  public ImmutableValue next() {
    return unpackedValues.remove();
  }

}
