package influent.internal.nio;

import java.nio.ByteBuffer;

public interface NioChannel extends AutoCloseable {
  int read(final ByteBuffer dst);
  int write(final ByteBuffer src);
  @Override
  void close();
}
