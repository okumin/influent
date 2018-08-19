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

package influent.internal.nio;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import influent.exception.InfluentIOException;

public class NioChannelConfig {

  private boolean sslEnabled = false;
  private String host;
  private int port;
  private String[] tlsVersions;
  private String[] ciphers;
  private SSLContext context;

  public NioChannelConfig() {
    sslEnabled = false;
    context = null;
    tlsVersions = null;
  }

  public NioChannelConfig(
      final String host,
      final int port,
      final boolean sslEnabled,
      final String[] tlsVersions,
      final String[] ciphers,
      final String keystorePath,
      final String keystorePassword,
      final String keyPassword) {
    this.host = host;
    this.port = port;
    this.sslEnabled = sslEnabled;
    this.tlsVersions = tlsVersions;
    this.ciphers = ciphers;
    try {
      if (isSslEnabled()) {
        context = SSLContext.getInstance("TLS");
        context.init(
            createKeyManagers(keystorePath, keystorePassword, keyPassword),
            null,
            new SecureRandom());
      }
    } catch (final NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (final KeyManagementException e) {
      e.printStackTrace();
    }
  }

  public SSLEngine createSSLEngine() {
    final SSLEngine engine = context.createSSLEngine(host, port);
    engine.setUseClientMode(false);
    engine.setEnabledProtocols(tlsVersions);
    if (ciphers != null) {
      engine.setEnabledCipherSuites(ciphers);
    }
    try {
      engine.beginHandshake();
    } catch (final SSLException e) {
      throw new InfluentIOException("Failed beginning a handshake.", e);
    }
    // TODO configure engine
    return engine;
  }

  private KeyManager[] createKeyManagers(
      final String filepath, final String keystorePassword, final String keyPassword) {
    try {
      final KeyStore keyStore = KeyStore.getInstance("JKS");
      try (InputStream keyStoreIS = new FileInputStream(filepath)) {
        keyStore.load(keyStoreIS, keystorePassword.toCharArray());
      }
      final KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, keyPassword.toCharArray());
      return kmf.getKeyManagers();
    } catch (final IOException e) {
      e.printStackTrace();
    } catch (final CertificateException
        | UnrecoverableKeyException
        | NoSuchAlgorithmException
        | KeyStoreException e) {
      e.printStackTrace();
    }
    return null;
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }
}
