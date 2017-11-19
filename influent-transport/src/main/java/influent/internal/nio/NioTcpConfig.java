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

import java.util.Objects;
import java.util.OptionalInt;

public final class NioTcpConfig {
  public static final class Builder {
    private int backlog = 0;
    private int sendBufferSize = 0;
    private int receiveBufferSize = 0;
    private boolean keepAliveEnabled = true;
    private boolean tcpNoDelayEnabled = true;

    /**
     * Sets the maximum number of pending connections for a server.
     *
     * @param backlog the maximum number of pending connections
     *                when 0 is given, the default value of JDK is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder backlog(final int backlog) {
      if (backlog < 0) {
        throw new IllegalArgumentException("Backlog must be greater than or equal to 0. value = "
            + backlog);
      }
      this.backlog = backlog;
      return this;
    }

    /**
     * Sets the SO_SNDBUF.
     *
     * @param sendBufferSize the size of socket send buffers
     *                       when 0 is given, the default value is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder sendBufferSize(final int sendBufferSize) {
      if (sendBufferSize < 0) {
        throw new IllegalArgumentException(
            "Buffer size must be greater than or equal to 0. value = " + sendBufferSize);
      }
      this.sendBufferSize = sendBufferSize;
      return this;
    }

    /**
     * Sets the SO_RCVBUF.
     *
     * @param receiveBufferSize the size of socket receive buffers
     *                           when 0 is given, the default value is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder receiveBufferSize(final int receiveBufferSize) {
      if (receiveBufferSize < 0) {
        throw new IllegalArgumentException(
            "Buffer size must be greater than or equal to 0. value = " + receiveBufferSize);
      }
      this.receiveBufferSize = receiveBufferSize;
      return this;
    }

    /**
     * Sets the SO_KEEPALIVE.
     *
     * @param keepAliveEnabled whether the SO_KEEPALIVE is enabled or not
     * @return this builder
     */
    public Builder keepAliveEnabled(final boolean keepAliveEnabled) {
      this.keepAliveEnabled = keepAliveEnabled;
      return this;
    }

    /**
     * Sets the TCP_NODELAY.
     *
     * @param tcpNoDelayEnabled whether TCP_NODELAY is enabled or not
     * @return this builder
     */
    public Builder tcpNoDelayEnabled(final boolean tcpNoDelayEnabled) {
      this.tcpNoDelayEnabled = tcpNoDelayEnabled;
      return this;
    }

    public NioTcpConfig build() {
      return new NioTcpConfig(backlog, sendBufferSize, receiveBufferSize, keepAliveEnabled,
          tcpNoDelayEnabled);
    }
  }

  private final int backlog;
  private final int sendBufferSize;
  private final int receiveBufferSize;
  private final boolean keepAliveEnabled;
  private final boolean tcpNoDelayEnabled;

  NioTcpConfig(final int backlog, final int sendBufferSize, final int receiveBufferSize,
      final boolean keepAliveEnabled, final boolean tcpNoDelayEnabled) {
    this.backlog = backlog;
    this.sendBufferSize = sendBufferSize;
    this.receiveBufferSize = receiveBufferSize;
    this.keepAliveEnabled = keepAliveEnabled;
    this.tcpNoDelayEnabled = tcpNoDelayEnabled;
  }

  /**
   * Returns the maximum number of pending connections for a server.
   * Use default value when {@code OptionalInt.empty()} is returned.
   */
  OptionalInt getBacklog() {
    return backlog == 0 ? OptionalInt.empty() : OptionalInt.of(backlog);
  }

  /**
   * Returns the SO_SNDBUF.
   * Use default value when {@code OptionalInt.empty()} is returned.
   */
  OptionalInt getSendBufferSize() {
    return sendBufferSize == 0 ? OptionalInt.empty() : OptionalInt.of(sendBufferSize);
  }

  /**
   * Returns the SO_RCVBUF.
   * Use default value when {@code OptionalInt.empty()} is returned.
   */
  OptionalInt getReceiveBufferSize() {
    return receiveBufferSize == 0 ? OptionalInt.empty() : OptionalInt.of(receiveBufferSize);
  }

  /**
   * Returns the SO_KEEPALIVE.
   */
  boolean getKeepAliveEnabled() {
    return keepAliveEnabled;
  }

  /**
   * Returns the TCP_NODELAY.
   */
  boolean getTcpNoDelayEnabled() {
    return tcpNoDelayEnabled;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final NioTcpConfig that = (NioTcpConfig) o;
    return backlog == that.backlog && sendBufferSize == that.sendBufferSize
        && receiveBufferSize == that.receiveBufferSize && keepAliveEnabled == that.keepAliveEnabled
        && tcpNoDelayEnabled == that.tcpNoDelayEnabled;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(backlog, sendBufferSize, receiveBufferSize, keepAliveEnabled,
        tcpNoDelayEnabled);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NioTcpConfig{" + "backlog=" + backlog + ", sendBufferSize=" + sendBufferSize
        + ", receiveBufferSize=" + receiveBufferSize + ", keepAliveEnabled=" + keepAliveEnabled
        + ", tcpNoDelayEnabled=" + tcpNoDelayEnabled + '}';
  }
}
