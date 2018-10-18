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

package example;

import influent.forward.ForwardCallback;
import influent.forward.ForwardSecurity;
import influent.forward.ForwardServer;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Print {
  private static final Logger logger = LoggerFactory.getLogger(Print.class);

  public static void main(final String[] args) throws Exception {
    final long durationSeconds = Environment.getLong("DURATION_SECONDS", 60);
    final boolean tlsEnabled = Environment.getBoolean("TLS_ENABLED", false);
    final String tlsKeystorePath = Environment.get("TLS_KEYSTORE_PATH", "/influent/influent-server.jks");
    final String tlsKeystorePassword = Environment.get("TLS_KEYSTORE_PASSWORD", "password");
    final String tlsKeyPassword = Environment.get("TLS_KEY_PASSWORD", "password");
    final boolean handshakeEnabled = Environment.getBoolean("HANDSHAKE_ENABLED", false);
    final String handshakeHostname = Environment.get("HANDSHAKE_HOSTNAME", "influent-server");
    final String handshakeSharedKey = Environment.get("HANDSHAKE_SHARED_KEY", "shared_key");

    final ForwardCallback callback = ForwardCallback.ofSyncConsumer(
        stream -> logger.info(stream.toString()),
        Executors.newWorkStealingPool()
    );

    final ForwardServer.Builder builder = new ForwardServer.Builder(callback);
    if (tlsEnabled) {
      builder
          .sslEnabled(true)
          .keystorePath(tlsKeystorePath)
          .keystorePassword(tlsKeystorePassword)
          .keyPassword(tlsKeyPassword);
    }
    if (handshakeEnabled) {
      final ForwardSecurity security = new ForwardSecurity
          .Builder()
          .selfHostname(handshakeHostname)
          .sharedKey(handshakeSharedKey)
          .build();
      builder.security(security);
    }

    final ForwardServer server = builder.build();
    server.start();

    // ForwardServer#start returns immediately
    Thread.sleep(durationSeconds * 1000);

    server.shutdown().get();
  }
}
