package sample;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import influent.forward.ForwardCallback;
import influent.forward.ForwardServer;

public class Counter {
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
    final Reporter reporter = new Reporter();

    final ForwardCallback callback = ForwardCallback.of(stream -> {
      reporter.add(stream.getEntries().size());
      return CompletableFuture.completedFuture(null);
    });

    final ForwardServer server = new ForwardServer.Builder(callback).build();
    server.start();
    new Thread(reporter).start();
  }
}
