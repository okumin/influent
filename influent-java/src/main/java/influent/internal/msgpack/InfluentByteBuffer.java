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

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import influent.internal.nio.NioTcpChannel;

final class InfluentByteBuffer {
  private static final int BUFFER_SIZE = 1024;

  private final Deque<ByteBuffer> buffers = new LinkedList<>();
  private long remaining = 0;
  private long bufferSizeLimit;

  InfluentByteBuffer(final long bufferSizeLimit) {
    this.bufferSizeLimit = bufferSizeLimit;
  }

  void push(final ByteBuffer buffer) {
    buffer.flip();
    remaining += buffer.remaining();
    buffers.addLast(buffer.slice());
  }

  private ByteBuffer peek() {
    return buffers.getFirst();
  }

  private void trim() {
    if (!peek().hasRemaining()) {
      buffers.removeFirst();
    }
  }

  private void getFromHead(final ByteBuffer dst) {
    final ByteBuffer head = peek();
    if (head.remaining() <= dst.remaining()) {
      remaining -= head.remaining();
      dst.put(head);
    } else {
      final int length = dst.remaining();
      remaining -= length;
      dst.put(head.array(), head.arrayOffset() + head.position(), length);
      head.position(head.position() + length);
    }

    trim();
  }

  boolean feed(final NioTcpChannel channel) {
    // TODO: optimization
    while (remaining < bufferSizeLimit) {
      final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      final int readSize = channel.read(buffer);
      if (readSize <= 0) {
        return false;
      }

      push(buffer);
    }
    return true;
  }

  boolean hasRemaining() {
    return remaining != 0;
  }

  long remaining() {
    return remaining;
  }

  void get(final ByteBuffer dst) {
    while (dst.hasRemaining() && hasRemaining()) {
      getFromHead(dst);
    }
  }

  byte getByte() {
    final byte head = peek().get();
    remaining -= 1;
    trim();
    return head;
  }
}
