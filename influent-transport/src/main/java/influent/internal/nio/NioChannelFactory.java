package influent.internal.nio;

import java.nio.channels.SocketChannel;

public class NioChannelFactory {
  public static NioChannel create(final SocketChannel channel,
                                  final int sendBufferSize,
                                  final boolean keepAliveEnabled,
                                  final boolean tcpNoDelayEnabled,
                                  final NioChannelConfig sslConfig) {
    if (sslConfig.isSslEnabled()) {
      return new NioSslChannel(channel, sendBufferSize, keepAliveEnabled, tcpNoDelayEnabled, sslConfig);
    } else {
      return new NioTcpChannel(channel, sendBufferSize, keepAliveEnabled, tcpNoDelayEnabled, sslConfig);
    }
  }
}
