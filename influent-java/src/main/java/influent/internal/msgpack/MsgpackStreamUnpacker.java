package influent.internal.msgpack;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

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
  public void feed(final NioTcpChannel channel) {
    boolean toBeContinued = true;
    while (toBeContinued) {
      toBeContinued = buffer.feed(channel);
      unpack(channel);
    }
  }

  // fails when the chunk size exceeds the limit
  private void unpack(final NioTcpChannel channel) {
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
