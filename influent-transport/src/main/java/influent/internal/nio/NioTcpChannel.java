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

/** A non-blocking {@code SocketChannel}. */
public interface NioTcpChannel extends AutoCloseable {
  /**
   * Writes bytes to the socket buffer.
   *
   * @param src the buffer
   * @return true when some bytes are written
   * @throws InfluentIOException if some IO error occurs
   */
  boolean write(final ByteBuffer src);

  /**
   * Reads bytes from the socket buffer.
   *
   * @param dst the buffer
   * @return true when some bytes are read
   * @throws InfluentIOException if some IO error occurs
   */
  boolean read(final ByteBuffer dst);

  /**
   * Registers the this channel to the given {@code NioEventLoop}. This method is thread-safe.
   *
   * @param opReadEnabled whether OP_READ is enabled or not
   * @param opWriteEnabled whether OP_WRITE is enabled or not
   * @param attachment the {@code NioAttachment}
   */
  void register(
      final boolean opReadEnabled, final boolean opWriteEnabled, final NioAttachment attachment);

  /** Enables OP_READ. Operations are done asynchronously. */
  void enableOpRead();

  /** Enables OP_WRITE. Operations are done asynchronously. */
  void enableOpWrite();

  /** Disables OP_WRITE. Operations are done asynchronously. */
  void disableOpWrite();

  /** Closes the {@code SocketChannel}. */
  @Override
  void close();

  /** @return true if this channel is open */
  boolean isOpen();

  /** @return the remote address */
  SocketAddress getRemoteAddress();
}
