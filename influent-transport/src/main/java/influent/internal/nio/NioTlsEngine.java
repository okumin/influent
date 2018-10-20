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

import influent.exception.InfluentIOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

/** A wrapped {@code SSLEngine}. */
public final class NioTlsEngine {
  private final SSLEngine engine;

  private NioTlsEngine(final SSLEngine engine) {
    this.engine = engine;
  }

  /**
   * Creates a {@code NioTlsEngine} with server mode.
   *
   * @param engine the {@code SSLEngine}
   * @return the {@code NioTlsEngine}
   */
  public static NioTlsEngine createServerEngine(final SSLEngine engine) {
    if (engine.getUseClientMode()) {
      // TODO: configure in this class
      throw new AssertionError();
    }
    return new NioTlsEngine(engine);
  }

  /** Returns the current handshake status. */
  HandshakeStatus getHandshakeStatus() {
    return engine.getHandshakeStatus();
  }

  /**
   * Encodes application data into network data.
   *
   * @param src application data
   * @param dst network data
   * @return the {@code SSLEngineResult}
   * @throws InfluentIOException when some operations about TLS fails
   * @throws ReadOnlyBufferException when the {@code dst} is a read-only buffer
   * @throws IllegalArgumentException when the {@code src} or the {@code dst} are {@code null}
   */
  SSLEngineResult wrap(final ByteBuffer src, final ByteBuffer dst) {
    try {
      return engine.wrap(src, dst);
    } catch (final SSLException e) {
      throw new InfluentIOException("Illegal SSL/TLS processing was detected.", e);
    } catch (final IllegalStateException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Decodes network data into application data.
   *
   * @param src network data
   * @param dst application data
   * @return the {@code SSLEngineResult}
   * @throws InfluentIOException when some operations about TLS fails
   * @throws ReadOnlyBufferException when the {@code dst} is a read-only buffer
   * @throws IllegalArgumentException when the {@code src} or the {@code dst} are {@code null}
   */
  SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer dst) {
    try {
      return engine.unwrap(src, dst);
    } catch (final SSLException e) {
      throw new InfluentIOException("Illegal SSL/TLS processing was detected.", e);
    } catch (final IllegalStateException e) {
      throw new AssertionError(e);
    }
  }

  /** Run all the delegated task. */
  void completeDelegatedTasks() {
    while (true) {
      final Runnable task = engine.getDelegatedTask();
      if (task == null) {
        break;
      }
      task.run();
    }
  }
}
