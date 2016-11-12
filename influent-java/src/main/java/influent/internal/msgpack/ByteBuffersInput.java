package influent.internal.msgpack;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.msgpack.core.buffer.MessageBuffer;
import org.msgpack.core.buffer.MessageBufferInput;

/**
 * {@code MessageBufferInput} which is composed of multiple {@code ByteBuffers}.
 */
final class ByteBuffersInput implements MessageBufferInput {
  private BufferList.Cell next;

  /**
   * Constructs a new {@code ByteBuffersInput}.
   *
   * @param buffers {@code ByteBuffers}
   */
  ByteBuffersInput(final BufferList buffers) {
    this.next = buffers.head();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MessageBuffer next() throws IOException {
    if (next == null) {
      return null;
    }

    final ByteBuffer buffer = next.getBuffer();
    next = next.next();
    return MessageBuffer.wrap(buffer);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
  }
}
