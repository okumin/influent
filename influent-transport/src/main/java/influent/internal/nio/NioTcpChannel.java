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

package influent.internal.nio;

import influent.exception.InfluentIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Set;

/**
 * A non-blocking {@code SocketChannel}.
 *
 * <p>Some operations of NioTcpChannel are not thread-safe.
 */
public interface NioTcpChannel extends AutoCloseable {
  enum Op {
    /** OP_READ */
    READ(SelectionKey.OP_READ),
    /** OP_WRITE */
    WRITE(SelectionKey.OP_WRITE);

    private final int bit;

    Op(final int bit) {
      this.bit = bit;
    }

    int getBit() {
      return bit;
    }

    static int bits(final Set<Op> ops) {
      return ops.stream().mapToInt(Op::getBit).reduce(0, (x, y) -> x | y);
    }
  }

  /**
   * Writes bytes to the socket buffer.
   *
   * <p>This method is not guaranteed to be thread-safe.
   *
   * @param src the buffer
   * @return true when some bytes are written
   * @throws InfluentIOException if some IO error occurs
   */
  boolean write(final ByteBuffer src);

  /**
   * Reads bytes from the socket buffer.
   *
   * <p>This method is not guaranteed to be thread-safe.
   *
   * @param dst the buffer
   * @return true when some bytes are read
   * @throws InfluentIOException if some IO error occurs
   */
  boolean read(final ByteBuffer dst);

  /**
   * Registers the this channel to the given {@code NioEventLoop}.
   *
   * <p>This method is thread-safe.
   *
   * @param ops the operations to be enabled
   * @param attachment the {@code NioAttachment}
   */
  void register(final Set<Op> ops, final NioAttachment attachment);

  /**
   * Enables the given operation.
   *
   * <p>This method is thread-safe and operations are done asynchronously.
   *
   * @param op the operation to be enabled
   */
  void enable(final Op op);

  /**
   * Disables the given operation.
   *
   * <p>This method is thread-safe and operations are done asynchronously.
   *
   * @param op the operation to be disabled
   */
  void disable(final Op op);

  /**
   * Closes the {@code SocketChannel}.
   *
   * <p>This method is not guaranteed to be thread-safe.
   */
  @Override
  void close();

  /**
   * Returns true if this channel is open
   *
   * <p>This method is not guaranteed to be thread-safe.
   */
  boolean isOpen();

  /**
   * Returns the remote address
   *
   * <p>This method is thread-safe.
   */
  SocketAddress getRemoteAddress();
}
