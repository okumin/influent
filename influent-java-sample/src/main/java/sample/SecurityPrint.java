package sample;

import influent.forward.ForwardCallback;
import influent.forward.ForwardSecurity;
import influent.forward.ForwardServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;

public class SecurityPrint {
  private static final Logger logger = LoggerFactory.getLogger(SecurityPrint.class);

  public static void main(final String[] args) throws Exception {
    final long durationSeconds = Long.valueOf(args[0]);

    final ForwardCallback callback = ForwardCallback.ofSyncConsumer(
        stream -> logger.info(stream.toString()),
        Executors.newWorkStealingPool()
    );

    final ForwardSecurity security = new ForwardSecurity
        .Builder()
        .selfHostname("input.testing.local")
        .sharedKey("secure_communication_is_awesome")
        .build();
    final ForwardServer server = new ForwardServer
        .Builder(callback)
        .workerPoolSize(1)
        .keepAliveEnabled(true)
        .security(security)
        .build();
    server.start();

    // ForwardServer#start returns immediately
    Thread.sleep(durationSeconds * 1000);

    server.shutdown().get();
  }
}
