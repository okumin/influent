package influent.internal.msgpack;

import java.nio.ByteBuffer;

/**
 * A buffer list.
 */
final class BufferList {
  /**
   * A cell of lists.
   */
  final class Cell {
    private final ByteBuffer buffer;
    private Cell next;

    private Cell(final ByteBuffer buffer) {
      this.buffer = buffer;
      this.next = null;
    }

    private void next(final ByteBuffer buffer) {
      next = new Cell(buffer);
    }

    /**
     * @return the current buffer
     */
    ByteBuffer getBuffer() {
      return buffer;
    }

    /**
     * @return the next cell
     */
    Cell next() {
      return next;
    }
  }

  private Cell head;
  private Cell last;
  private long sizeInBytes = 0;

  /**
   * Constructs the empty {@code BufferList}.
   */
  BufferList() {
    this.head = null;
    this.last = null;
  }

  /**
   * Adds a {@code ByteBuffer} into this list.
   *
   * @param buffer {@code ByteBuffer}
   */
  void add(final ByteBuffer buffer) {
    sizeInBytes += buffer.remaining();
    if (head != null) {
      last.next(buffer);
      last = last.next();
    } else {
      head = new Cell(buffer);
      last = head;
    }
  }

  /**
   * Removes the given bytes.
   *
   * @param dropSize the size to drop
   */
  void dropBytes(long dropSize) {
    sizeInBytes -= dropSize;
    while (dropSize > 0) {
      final int headLength = head.buffer.remaining();
      if (headLength > dropSize) {
        head.buffer.position(head.buffer.position() + (int) dropSize);
        break;
      }
      this.head = head.next;
      dropSize -= headLength;
    }
  }

  /**
   * @return the buffer size
   */
  long sizeInBytes() {
    return sizeInBytes;
  }

  /**
   * @return the head of this list
   */
  Cell head() {
    return head;
  }
}
