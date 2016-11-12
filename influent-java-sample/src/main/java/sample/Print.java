package sample;

import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import influent.forward.ForwardCallback;
import influent.forward.ForwardServer;

public final class Print {
  private static final Logger logger = LoggerFactory.getLogger(Print.class);

  public static void main(final String[] args) throws Exception {
    final long durationSeconds = Long.valueOf(args[0]);

    final ForwardCallback callback = ForwardCallback.ofSyncConsumer(
        stream -> logger.info(stream.toString()),
        Executors.newWorkStealingPool()
    );

    final ForwardServer server = new ForwardServer.Builder(callback).build();
    server.start();

    // ForwardServer#start returns immediately
    Thread.sleep(durationSeconds * 1000);

    server.shutdown().get();
  }
}
