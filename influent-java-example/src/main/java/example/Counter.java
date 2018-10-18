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
import influent.forward.ForwardServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    final int workerPoolSize = Integer.parseInt(args[0]);

    final Reporter reporter = new Reporter();

    final ForwardCallback callback = ForwardCallback.of(stream -> {
      reporter.add(stream.getEntries().size());
      return CompletableFuture.completedFuture(null);
    });

    final ForwardServer server = new ForwardServer
        .Builder(callback)
        .workerPoolSize(workerPoolSize)
        .build();
    server.start();
    new Thread(reporter).start();
  }
}
