package influent.forward;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A server which accepts requests of Fluentd's forward protocol.
 */
public interface ForwardServer {
  /**
   * A builder of {@code ForwardServer}.
   */
  class Builder {
    private static int DEFAULT_WORKER_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    private static final int DEFAULT_PORT = 24224;

    private final ForwardCallback forwardCallback;

    private SocketAddress localAddress = new InetSocketAddress(DEFAULT_PORT);
    private long chunkSizeLimit = Long.MAX_VALUE;
    private int backlog = 0;
    private int sendBufferSize = 0;
    private int receiveBufferSize = 0;
    private boolean keepAliveEnabled = true;
    private boolean tcpNoDelayEnabled = true;
    private int workerPoolSize = 0;

    /**
     * Constructs a new {@code ForwardServer.Builder}.
     *
     * @param forwardCallback the callback function that consumes {@code EventStreams}
     * @throws NullPointerException if some of arguments are null
     */
    public Builder(final ForwardCallback forwardCallback) {
      this.forwardCallback = Objects.requireNonNull(forwardCallback);
    }

    /**
     * Sets the local address.
     *
     * @param value the local address of the forward server
     * @return this builder
     */
    public Builder localAddress(final SocketAddress value) {
      this.localAddress = value;
      return this;
    }

    /**
     * Sets the local address.
     *
     * @param port the port of the local address
     * @return this builder
     */
    public Builder localAddress(final int port) {
      return localAddress(new InetSocketAddress(port));
    }

    /**
     * Sets the allowable chunk size.
     * The connection which sends a chunk larger than this limit may be disconnected.
     *
     * @param value the allowable chunk size
     * @return this builder
     * @throws IllegalArgumentException when the size is less than or equal to 0
     */
    public Builder chunkSizeLimit(final long value) {
      if (value <= 0) {
        throw new IllegalArgumentException("Chunk size limit must be greater than 0.");
      }
      this.chunkSizeLimit = value;
      return this;
    }

    /**
     * Sets the maximum number of pending connections for a server.
     *
     * @param value the maximum number of pending connections
     *              when 0 is given, the default value of JDK is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder backlog(final int value) {
      if (value < 0) {
        throw new IllegalArgumentException("Backlog must be greater than or equal to 0.");
      }
      backlog = value;
      return this;
    }

    /**
     * Sets the SO_SNDBUF for forward connections.
     *
     * @param value the size of socket send buffers
     *              when 0 is given, the default value is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder sendBufferSize(final int value) {
      if (value < 0) {
        throw new IllegalArgumentException("Buffer size must be greater than or equal to 0.");
      }
      sendBufferSize = value;
      return this;
    }

    /**
     * Sets the SO_RCVBUF for forward connections.
     *
     * @param value the size of socket receive buffers
     *              when 0 is given, the default value is used
     * @return this builder
     * @throws IllegalArgumentException when the size is less than 0
     */
    public Builder receiveBufferSize(final int value) {
      if (value < 0) {
        throw new IllegalArgumentException("Buffer size must be greater than or equal to 0.");
      }
      receiveBufferSize = value;
      return this;
    }

    /**
     * Sets the SO_KEEPALIVE configuration.
     *
     * @param value whether the SO_KEEPALIVE is enabled or not
     * @return this builder
     */
    public Builder keepAliveEnabled(final boolean value) {
      keepAliveEnabled = value;
      return this;
    }

    /**
     * Sets the TCP_NODELAY configuration.
     *
     * @param value whether TCP_NODELAY is enabled or not
     * @return this builder
     */
    public Builder tcpNoDelayEnabled(final boolean value) {
      tcpNoDelayEnabled = value;
      return this;
    }

    /**
     * Sets the event loop pool size.
     * The larger {@code poolSize} is given, the larger number of threads concurrently run.
     *
     * @param value the event loop pool size
     * @return this builder
     */
    public Builder workerPoolSize(final int value) {
      if (value <= 0) {
        throw new IllegalArgumentException("Buffer size must be greater than 0.");
      }
      workerPoolSize = value;
      return this;
    }

    /**
     * Creates a new {@code ForwardServer}.
     *
     * @return the new {@code ForwardServer}
     * @throws IllegalArgumentException if any of parameter is invalid
     *                                  e.g. the local address is already used
     * @throws influent.exception.InfluentIOException if some IO error occurs
     */
    public ForwardServer build() {
      return new NioForwardServer(
          localAddress,
          forwardCallback,
          chunkSizeLimit,
          backlog,
          sendBufferSize,
          receiveBufferSize,
          keepAliveEnabled,
          tcpNoDelayEnabled,
          workerPoolSize == 0 ? DEFAULT_WORKER_POOL_SIZE : workerPoolSize
      );
    }
  }

  /**
   * Starts and spawns this {@code ForwardServer}.
   */
  default void start() {
    start(Executors.defaultThreadFactory());
  }

  /**
   * Starts and spawns this {@code ForwardServer}.
   *
   * @param threadFactory the {@code ThreadFactory}
   */
  void start(final ThreadFactory threadFactory);

  /**
   * Terminates this {@code ForwardServer}.
   * Shutdown operations are executed asynchronously
   * and {@code ForwardServer#shutdown} returns a {@code CompletedFuture}.
   *
   * @return {@code CompletableFuture} that will be completed when this {@code ForwardServer} stops
   */
  CompletableFuture<Void> shutdown();
}
