package influent.forward;

import influent.internal.msgpack.MsgpackStreamUnpacker;
import influent.internal.nio.NioAttachment;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioSslChannel;
import influent.internal.util.ThreadSafeQueue;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioSslForwardConnection implements NioAttachment {
  private static final Logger logger = LoggerFactory.getLogger(NioSslForwardConnection.class);
  private static final String ACK_KEY = "ack";

  private final NioSslChannel channel;
  private final NioEventLoop eventLoop;
  private final ForwardCallback callback;
  private final MsgpackStreamUnpacker unpacker;
  private final MsgpackForwardRequestDecoder decoder;

  final ThreadSafeQueue<ByteBuffer> responses = new ThreadSafeQueue<>();

  NioSslForwardConnection(final NioSslChannel channel,
                            final NioEventLoop eventLoop,
                            final ForwardCallback callback,
                            final MsgpackStreamUnpacker unpacker,
                            final MsgpackForwardRequestDecoder decoder) {
      this.channel = channel;
      this.eventLoop = eventLoop;
      this.callback = callback;
      this.unpacker = unpacker;
      this.decoder = decoder;
  }

  NioSslForwardConnection(final NioSslChannel channel,
                          final NioEventLoop eventLoop,
                          final ForwardCallback callback,
                          final long chunkSizeLimit) {
      this(
          channel,
          eventLoop,
          callback,
          new MsgpackStreamUnpacker(chunkSizeLimit),
          new MsgpackForwardRequestDecoder()
      );
  }

  NioSslForwardConnection(SocketChannel socketChannel,
                          NioEventLoop eventLoop,
                          ForwardCallback callback,
                          long chunkSizeLimit,
                          int sendBufferSize,
                          boolean keepAliveEnabled,
                          boolean tcpNoDelayEnabled) {
    this(
        new NioSslChannel(socketChannel, sendBufferSize, keepAliveEnabled, tcpNoDelayEnabled),
        eventLoop,
        callback,
        chunkSizeLimit
    );

    channel.register(eventLoop, SelectionKey.OP_READ, this);
  }

  @Override
  public void onReadable(SelectionKey key) {
    receiveRequests(key);
    if (!channel.isOpen()) {
      close();
    }
  }

  private void receiveRequests(SelectionKey key) {
    unpacker.feed(channel);
    while (unpacker.hasNext()) {
      try {
        decoder.decode(unpacker.next()).ifPresent(result -> {
          logger.debug(
              "Received a forward request from {}. chunk_id = {}",
              channel.getRemoteAddress(), result.getOption()
          );
          callback.consume(result.getStream()).thenRun(() -> {
            // Executes on user's callback thread since the queue never block.
            result.getOption().getChunk().ifPresent(chunk -> completeTask(key, chunk));
            logger.debug("Completed the task. chunk_id = {}.", result.getOption());
          });
        });
      } catch (final IllegalArgumentException e) {
        logger.error(
            "Received an invalid message. remote address = " + channel.getRemoteAddress(), e
        );
      }
    }
  }

  @Override
  public void onWritable(SelectionKey key) {

  }

  // This method is thread-safe.
  private void completeTask(final SelectionKey key, final String chunk) {
    try {
      final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
      packer.packMapHeader(1);
      packer.packString(ACK_KEY);
      packer.packString(chunk);
      final ByteBuffer buffer = packer.toMessageBuffer().sliceAsByteBuffer();
      responses.enqueue(buffer);
      eventLoop.enableInterestSet(key, SelectionKey.OP_WRITE);
    } catch (final IOException e) {
      logger.error("Failed packing. chunk = " + chunk, e);
    }
  }

  @Override
  public void close() {

  }
}
