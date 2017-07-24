package influent.internal.nio;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class NioAcceptorFactory {
  public static NioAttachment create(final SocketAddress localAddress,
                                     final NioEventLoop eventLoop,
                                     final Consumer<SocketChannel> callback,
                                     final int backlog,
                                     final int receiveBufferSize,
                                     final NioChannelConfig channelConfig) {
    if (channelConfig.isSslEnabled()) {
      return new NioSslAcceptor(localAddress, eventLoop, callback, backlog, receiveBufferSize, channelConfig);
    } else {
      return new NioTcpAcceptor(localAddress, eventLoop, callback, backlog, receiveBufferSize);
    }
  }
}
