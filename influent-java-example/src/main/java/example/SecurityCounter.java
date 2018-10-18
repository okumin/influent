package example;

import influent.forward.ForwardCallback;
import influent.forward.ForwardSecurity;
import influent.forward.ForwardServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityCounter {
  private static final class Reporter implements Runnable {
    private final AtomicLong counter = new AtomicLong();

    void add(final int up) {
      counter.addAndGet(up);
    }

    @Override
    public void run() {
      long lastChecked = System.currentTimeMillis();
      while (true) {
        try {
          Thread.sleep(100);
        } catch (final InterruptedException e) {
          break;
        }
        final long now = System.currentTimeMillis();
        if (now - lastChecked >= 1000) {
          lastChecked = now;
          final long current = counter.getAndSet(0);
          logger.info("{} requests/sec", current);
        }
      }
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(Counter.class);

  public static void main(final String[] args) {
    final int workerPoolSize = Integer.parseInt(args[0]);

    final Reporter reporter = new Reporter();

    final ForwardCallback callback = ForwardCallback.of(stream -> {
      reporter.add(stream.getEntries().size());
      return CompletableFuture.completedFuture(null);
    });

    final ForwardSecurity security = new ForwardSecurity
        .Builder()
        .selfHostname("input.testing.local")
        .sharedKey("secure_communication_is_awesome")
        .build();
    final ForwardServer server = new ForwardServer
        .Builder(callback)
        .workerPoolSize(workerPoolSize)
        .security(security)
        .build();
    server.start();
    new Thread(reporter).start();
  }
}
