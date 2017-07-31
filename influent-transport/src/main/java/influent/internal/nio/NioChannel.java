package influent.internal.nio;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public interface NioChannel extends AutoCloseable {
  int read(final ByteBuffer dst);
  int write(final ByteBuffer src);
  @Override
  void close();
  boolean isOpen();
  void register(final NioEventLoop eventLoop,
                final int ops,
                final NioAttachment attachment);
  SocketAddress getRemoteAddress();
}
