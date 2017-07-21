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

package influent.forward;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioEventLoopPool;
import influent.internal.nio.NioSslAcceptor;
import influent.internal.nio.NioTcpAcceptor;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * A {@code ForwardServer} implemented by NIO.
 */
final class NioForwardServer implements ForwardServer {
  public enum Protocol { TCP, TLS }
  public enum TlsVersion {
    None("None"),
    TLSv1_1("TLSv1.1"),
    TLSv1_2("TLSv1.2");

    private final String version;

    TlsVersion(String s) {
      version = s;
    }

    @Override
    public String toString() {
      return version;
    }
  }
  private final NioEventLoop bossEventLoop;
  private final NioEventLoopPool workerEventLoopPool;
  private final Protocol protocol;
  private final TlsVersion tlsVersion;
  private SSLContext context = null;

  /**
   * Creates a {@code NioForwardServer}.
   *
   * @param localAddress local address to be bound
   * @param callback invoked when a request arrives
   * @param chunkSizeLimit the allowable size of chunks
   * @param backlog backlog
   * @param sendBufferSize enqueue buffer size, it must be greater than or equal to zero
   * @param receiveBufferSize dequeue buffer size, it must be greater than or equal to zero
   * @param keepAliveEnabled whether SO_KEEPALIVE is enabled or not
   * @param tcpNoDelayEnabled whether TCP_NODELAY is enabled or not
   * @param workerPoolSize the event loop pool size for workers
   * @throws IllegalArgumentException if any of parameter is invalid
   *                                  e.g. the local address is already used
   * @throws influent.exception.InfluentIOException if some IO error occurs
   */
  NioForwardServer(final SocketAddress localAddress,
                   final ForwardCallback callback,
                   final long chunkSizeLimit,
                   final int backlog,
                   final int sendBufferSize,
                   final int receiveBufferSize,
                   final boolean keepAliveEnabled,
                   final boolean tcpNoDelayEnabled,
                   final int workerPoolSize) {
    this(localAddress, callback, chunkSizeLimit, backlog,
        sendBufferSize, receiveBufferSize, keepAliveEnabled, tcpNoDelayEnabled,
        workerPoolSize, Protocol.TCP, TlsVersion.None);
    context = null;
  }

  NioForwardServer(final SocketAddress localAddress,
                   final ForwardCallback callback,
                   final long chunkSizeLimit,
                   final int backlog,
                   final int sendBufferSize,
                   final int receiveBufferSize,
                   final boolean keepAliveEnabled,
                   final boolean tcpNoDelayEnabled,
                   final int workerPoolSize,
                   final Protocol protocol,
                   final TlsVersion tlsVersion) {
    bossEventLoop = NioEventLoop.open();
    workerEventLoopPool = NioEventLoopPool.open(workerPoolSize);
    this.protocol = protocol;
    this.tlsVersion = tlsVersion;
    switch (protocol) {
      case TCP:
        context = null;
        final Consumer<SocketChannel> tcpChannelFactory = socketChannel -> new NioForwardConnection(
            socketChannel, workerEventLoopPool.next(), callback, chunkSizeLimit, sendBufferSize,
            keepAliveEnabled, tcpNoDelayEnabled
        );
        new NioTcpAcceptor(
            localAddress, bossEventLoop, tcpChannelFactory, backlog, receiveBufferSize
        );
        break;
      case TLS:
        try {
          context = SSLContext.getInstance(tlsVersion.toString());
          context.init(
              createKeyManagers("", "", ""),
              createTrustManagers("", ""),
              new SecureRandom()
          );
        } catch (NoSuchAlgorithmException e) {
          throw new AssertionError(e.getMessage());
        } catch (KeyManagementException e) {
          e.printStackTrace();
        }
        final Consumer<SocketChannel> tlsChannelFactory = socketChannel -> new NioSslForwardConnection(
            socketChannel, workerEventLoopPool.next(), callback, chunkSizeLimit, sendBufferSize,
            keepAliveEnabled, tcpNoDelayEnabled, context
        );
        new NioSslAcceptor(
            localAddress, bossEventLoop, tlsChannelFactory, backlog, receiveBufferSize, context
        );
        break;
      default:
        throw new IllegalArgumentException("Unknow protocol: " + protocol.name());
    }
    new NioUdpHeartbeatServer(localAddress, bossEventLoop);
  }

  private KeyManager[] createKeyManagers(String filepath, String keystorePassword, String keyPassword) {
    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      InputStream keyStoreIS = new FileInputStream(filepath);
      try {
        keyStore.load(keyStoreIS, keystorePassword.toCharArray());
      } finally {
        if (keyStoreIS != null) {
          keyStoreIS.close();
        }
      }
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, keyPassword.toCharArray());
      return kmf.getKeyManagers();
    } catch (final IOException e) {
      e.printStackTrace();
    } catch (final CertificateException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
      e.printStackTrace();
    }
    return null;
  }

  private TrustManager[] createTrustManagers(String filepath, String keystorePassword) {
    try {
      KeyStore trustStore = KeyStore.getInstance("JKS");
      InputStream trustStoreIS = new FileInputStream(filepath);
      try {
        trustStore.load(trustStoreIS, keystorePassword.toCharArray());
      } finally {
        if (trustStoreIS != null) {
          trustStoreIS.close();
        }
      }
      TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustFactory.init(trustStore);
      return trustFactory.getTrustManagers();
    } catch (final IOException e) {
      e.printStackTrace();
    } catch (final CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void start(final ThreadFactory threadFactory) {
    workerEventLoopPool.start(threadFactory);
    threadFactory.newThread(bossEventLoop).start();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletableFuture<Void> shutdown() {
    return bossEventLoop.shutdown().thenCompose(x -> workerEventLoopPool.shutdown());
  }
}
