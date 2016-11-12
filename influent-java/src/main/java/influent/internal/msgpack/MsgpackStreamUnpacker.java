package influent.internal.msgpack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.msgpack.core.MessageInsufficientBufferException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ImmutableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger logger = LoggerFactory.getLogger(MsgpackStreamUnpacker.class);
  private static final int BUFFER_SIZE = 1024;

  private final BufferList buffers = new BufferList();
  private final MessageUnpacker unpacker =
      MessagePack.newDefaultUnpacker(new ByteBuffersInput(buffers));
  private final Queue<ImmutableValue> unpackedValues = new LinkedList<>();
  private final long chunkSizeLimit;

  private boolean isCompleted = false;

  /**
   * Constructs a new {@code MsgpackStreamUnpacker}.
   *
   * @param chunkSizeLimit the allowable chunk size
   *                       {@code read} fails when the size of reading chunk exceeds the limit
   */
  public MsgpackStreamUnpacker(final long chunkSizeLimit) {
    this.chunkSizeLimit = chunkSizeLimit;
  }

  /**
   * Reads buffers from a {@code ReadableByteChannel}.
   *
   * @param channel channel
   * @throws InfluentIOException when it fails reading from the channel
   *                             or the chunk size exceeds the limit
   */
  public void read(final NioTcpChannel channel) {
    // TODO: optimize
    boolean toBeContinued = true;
    while (toBeContinued) {
      toBeContinued = consume(channel);
      unpack();
    }
  }

  // read until the size of buffer exceeds the limit or socket buffer becomes empty
  // true if it can continue consuming
  private boolean consume(final NioTcpChannel channel) {
    while (buffers.sizeInBytes() < chunkSizeLimit) {
      final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      final int readSize = channel.read(buffer);
      if (readSize < 0) {
        // don't throw error since current chunks should be consumed even when the channel is closed
        isCompleted = true;
        return false;
      }
      if (readSize == 0) {
        return false; // the socket buffer is empty
      }
      buffer.flip();
      buffers.add(buffer.slice());
    }
    // the chunk limit exceeded
    // retry consuming after unpacking
    return true;
  }

  // fails when the chunk size exceeds the limit
  private void unpack() {
    while (true) {
      try {
        unpacker.reset(new ByteBuffersInput(buffers));
        final ImmutableValue value = unpacker.unpackValue();
        final long decodedSize = unpacker.getTotalReadBytes();
        buffers.dropBytes(decodedSize);
        unpackedValues.offer(value);
      } catch (final MessageInsufficientBufferException e) {
        logger.debug("Buffers has only insufficient data.");
        if (buffers.sizeInBytes() >= chunkSizeLimit) {
          isCompleted = true;
          throw new InfluentIOException("The chunk size exceeds the limit. size = "
              + buffers.sizeInBytes() + ", limit = " + chunkSizeLimit);
        }
        break; // retry after the next reading
      } catch (final IOException e) {
        isCompleted = true;
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
    if (unpackedValues.isEmpty()) {
      throw new NoSuchElementException("This unpacker has no next value.");
    }
    return unpackedValues.poll();
  }

  /**
   * @return true when the stream is completed
   */
  public boolean isCompleted() {
    return isCompleted;
  }
}
