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
import influent.internal.util.ThreadSafeQueue;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A non-blocking secure {@code SocketChannel}.
 *
 * <p>This implementation is naive and its performance is poor yet.
 */
public final class NioTcpTlsChannel implements NioTcpChannel {
  private static final Logger logger = LoggerFactory.getLogger(NioTcpTlsChannel.class);

  private final NioTcpPlaintextChannel channel;
  private final NioTlsEngine engine;

  private ByteBuffer inboundNetworkBuffer = newInboundNetworkBuffer();
  private final ThreadSafeQueue<ByteBuffer> outboundNetworkBuffers = new ThreadSafeQueue<>();
  private final Set<Op> applicationInterests = new CopyOnWriteArraySet<>();

  private static ByteBuffer newInboundNetworkBuffer() {
    // Prepare a ByteBuffer with sufficient size
    final ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
    buffer.position(buffer.limit());
    return buffer;
  }

  private NioTcpTlsChannel(final NioTcpPlaintextChannel channel, final NioTlsEngine engine) {
    this.channel = channel;
    this.engine = engine;
  }

  /**
   * Creates a new {@code NioTcpTlsChannel}.
   *
   * @param channel the accepted {@code SocketChannel}
   * @param eventLoop the {@code NioEventLoop}
   * @param tcpConfig the {@code NioTcpConfig}
   * @param engine the {@code SSLEngine}
   * @throws InfluentIOException if some IO error occurs
   */
  public static NioTcpTlsChannel open(
      final SocketChannel channel,
      final NioEventLoop eventLoop,
      final NioTcpConfig tcpConfig,
      final NioTlsEngine engine) {
    final NioTcpPlaintextChannel plaintextChannel =
        NioTcpPlaintextChannel.open(channel, eventLoop, tcpConfig);
    return new NioTcpTlsChannel(plaintextChannel, engine);
  }

  /** {@inheritDoc} */
  @Override
  public boolean write(final ByteBuffer src) {
    if (!handshake()) {
      return false;
    }
    final int current = src.position();
    flush(); // must flush at least once
    while (src.hasRemaining() && wrapAndSend(src).getStatus() == Status.OK) ;
    return src.position() != current;
  }

  /** {@inheritDoc} */
  @Override
  public boolean read(final ByteBuffer dst) {
    if (!handshake()) {
      return false;
    }
    final int current = dst.position();
    while (receiveAndUnwrap(dst).getStatus() == Status.OK) ;
    return dst.position() != current;
  }

  private boolean handshake() {
    final HandshakeStatus current = engine.getHandshakeStatus();
    logger.debug("Handshake with {}, status = {}", getRemoteAddress(), current);
    if (current == HandshakeStatus.NOT_HANDSHAKING) {
      return true;
    }
    final HandshakeStatus status = doHandshake(current);
    switch (status) {
        // FINISHED is returned by wrapping or unwrapping once the handshake is just finished
      case FINISHED:
        channel.disable(Op.READ);
        if (outboundNetworkBuffers.isEmpty()) {
          channel.disable(Op.WRITE);
        }
        applicationInterests.forEach(channel::enable);
        applicationInterests.clear();
        return true;
        // NOT_HANDSHAKE means that the handshake was already finished
        // This status appears when the TLS communication finishes
        // At the end of a TLS communication, the status may change as follows
        // 1. NOT_HANDSHAKING into NEED_WRAP to send a closure message
        // 2. NEED_WRAP to NOT_HANDSHAKING when the closure message is sent
      case NOT_HANDSHAKING: // already finished
        return true;
      case NEED_WRAP:
        channel.disable(Op.READ);
        channel.enable(Op.WRITE);
        return false;
      case NEED_UNWRAP:
        if (outboundNetworkBuffers.isEmpty()) {
          channel.disable(Op.WRITE);
        }
        channel.enable(Op.WRITE);
        return false;
        // case NEED_UNWRAP_AGAIN: JDK 9 has this new status for DTLS, but normal TLS does not use
      default:
        throw new AssertionError();
    }
  }

  private HandshakeStatus doHandshake(final HandshakeStatus status) {
    logger.debug("The current HandshakeStatus for {}: {}", getRemoteAddress(), status);
    // previous buffers may remain
    flush();

    switch (status) {
      case FINISHED:
      case NOT_HANDSHAKING:
        return status;
      case NEED_WRAP:
        final SSLEngineResult wrapResult = wrapAndSend(ByteBuffer.allocate(0));
        if (wrapResult.getStatus() != Status.OK) {
          return wrapResult.getHandshakeStatus();
        }
        return doHandshake(wrapResult.getHandshakeStatus());
      case NEED_UNWRAP:
        final SSLEngineResult unwrapResult = receiveAndUnwrap(ByteBuffer.allocate(1024));
        if (unwrapResult.getStatus() != Status.OK) {
          return unwrapResult.getHandshakeStatus();
        }
        return doHandshake(unwrapResult.getHandshakeStatus());
      case NEED_TASK:
        engine.completeDelegatedTasks();
        return doHandshake(engine.getHandshakeStatus());
        // case NEED_UNWRAP_AGAIN: JDK 9 has this new status for DTLS, but this is not used
      default:
        throw new AssertionError();
    }
  }

  private SSLEngineResult wrapAndSend(final ByteBuffer src) {
    final ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
    final SSLEngineResult result = engine.wrap(src, buffer);
    logger.debug("The wrapping result for {}: {}", getRemoteAddress(), result);
    switch (result.getStatus()) {
      case OK:
      case CLOSED:
        break;
      case BUFFER_OVERFLOW: // allocating large size currently
      case BUFFER_UNDERFLOW: // happens only when unwrapping
      default:
        throw new AssertionError();
    }

    buffer.flip();
    if (buffer.hasRemaining()) {
      outboundNetworkBuffers.enqueue(buffer);
    }

    flush();
    return result;
  }

  private void flush() {
    while (channel.isOpen() && outboundNetworkBuffers.nonEmpty()) {
      final ByteBuffer head = outboundNetworkBuffers.peek();
      if (!channel.write(head)) {
        break;
      }
      if (!head.hasRemaining()) {
        outboundNetworkBuffers.dequeue();
      }
    }
  }

  private SSLEngineResult receiveAndUnwrap(final ByteBuffer dst) {
    // the phase to receive new network data
    // mark = the index which has data received from network, but not consumed by application(dst)
    // position = the index in order to receive new network data
    // limit = the capacity
    if (inboundNetworkBuffer.hasRemaining()) {
      inboundNetworkBuffer.mark();
      inboundNetworkBuffer.position(inboundNetworkBuffer.limit());
      inboundNetworkBuffer.limit(inboundNetworkBuffer.capacity());
    } else {
      inboundNetworkBuffer.clear();
      inboundNetworkBuffer.mark();
    }
    while (channel.isOpen() && channel.read(inboundNetworkBuffer)) ;

    // the phase that the application consumes the non-consumed network data
    // position = the index to consume
    // limit = the index which has no new network data
    inboundNetworkBuffer.limit(inboundNetworkBuffer.position());
    inboundNetworkBuffer.reset();
    final SSLEngineResult result = engine.unwrap(inboundNetworkBuffer, dst);
    logger.debug("The unwrapping result for {}: {}", getRemoteAddress(), result);
    switch (result.getStatus()) {
      case OK:
      case BUFFER_UNDERFLOW: // inbound data is not enough
      case CLOSED:
        break;
      case BUFFER_OVERFLOW: // application buffer(dst) is not enough
        inboundNetworkBuffer.limit(inboundNetworkBuffer.position());
        return receiveAndUnwrap(dst);
      default:
        throw new AssertionError();
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void register(final Set<Op> ops, final NioAttachment attachment) {
    applicationInterests.addAll(ops);
    channel.register(EnumSet.of(Op.READ), attachment);
  }

  /** {@inheritDoc} */
  @Override
  public void enable(final Op op) {
    if (!handshake()) {
      applicationInterests.add(op);
      return;
    }
    channel.enable(op);
  }

  /** {@inheritDoc} */
  @Override
  public void disable(final Op op) {
    if (!handshake()) {
      applicationInterests.remove(op);
      return;
    }
    if (op == Op.WRITE && outboundNetworkBuffers.nonEmpty()) {
      // application has no more data to send, but TLS layer has remaining data to send
      return;
    }
    channel.disable(op);
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    channel.close();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  /** {@inheritDoc} */
  @Override
  public SocketAddress getRemoteAddress() {
    return channel.getRemoteAddress();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "NioTcpTlsChannel(" + getRemoteAddress() + ")";
  }
}
