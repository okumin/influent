package influent.internal.nio;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
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

  public NioChannelConfig(String host, int port, boolean sslEnabled, String[] tlsVersions, String[] ciphers,
                          String keystorePath, String keystorePassword, String keyPassword,
                          String truststroePath, String truststrorePassword) {
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
            createTrustManagers(truststroePath, truststrorePassword),
            new SecureRandom()
        );
      }
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (KeyManagementException e) {
      e.printStackTrace();
    }
  }

  public SSLEngine createSSLEngine() {
    SSLEngine engine = context.createSSLEngine(host, port);
    engine.setUseClientMode(false);
    engine.setEnabledProtocols(tlsVersions);
    if (ciphers != null) {
      engine.setEnabledCipherSuites(ciphers);
    }
    // TODO configure engine
    return engine;
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
    if (filepath == null) {
      return null;
    }
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

  public boolean isSslEnabled() {
    return sslEnabled;
  }
}
