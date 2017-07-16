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
