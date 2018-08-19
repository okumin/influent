package influent.forward;

import influent.internal.msgpack.MsgpackStreamUnpacker;
import influent.internal.nio.NioEventLoop;
import influent.internal.nio.NioTcpChannel;
import influent.internal.util.ThreadSafeQueue;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;

abstract class NioForwardConnection {
    private static final Logger logger = LoggerFactory.getLogger(NioForwardConnection.class);

    protected static final String ACK_KEY = "ack";
    protected final NioTcpChannel channel;
    protected final NioEventLoop eventLoop;
    protected final ForwardCallback callback;
    protected final MsgpackStreamUnpacker unpacker;
    protected final MsgpackForwardRequestDecoder decoder;
    protected final ForwardSecurity security;
    protected final ThreadSafeQueue<ByteBuffer> responses = new ThreadSafeQueue<>();

    protected final byte[] nonce = new byte[16];
    protected final byte[] userAuth = new byte[16];

    protected MsgPackPingDecoder pingDecoder;
    protected Optional<ForwardClientNode> node;
    protected ConnectionState state;

    enum ConnectionState {
        HELO, PINGPONG, ESTABLISHED
    }

    NioForwardConnection(final NioTcpChannel channel, final NioEventLoop eventLoop,
                         final ForwardCallback callback, final MsgpackStreamUnpacker unpacker,
                         final MsgpackForwardRequestDecoder decoder, final ForwardSecurity security) {
        state = ConnectionState.ESTABLISHED;
        this.channel = channel;
        this.eventLoop = eventLoop;
        this.callback = callback;
        this.unpacker = unpacker;
        this.decoder = decoder;
        this.security = security;

        if (security.isEnabled()) {
            try {
                // SecureRandom secureRandom = SecureRandom.getInstanceStrong();
                // Above secureRandom may block...
                // TODO: reuse SecureRandom instance
                SecureRandom secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking");
                logger.debug(secureRandom.getAlgorithm());
                secureRandom.nextBytes(nonce);
                secureRandom.nextBytes(userAuth);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            node = security.findNode(((InetSocketAddress) channel.getRemoteAddress()).getAddress());
            pingDecoder = new MsgPackPingDecoder(this.security, node.orElse(null), nonce, userAuth);
        }
    }

    // TODO Set keepalive on HELO message true/false according to ForwardServer configuration
    //      ForwardServer.keepAliveEnabled set SO_KEEPALIVE.
    //      See also https://github.com/okumin/influent/pull/32#discussion_r145196969
    protected ByteBuffer generateHelo() {
      // ['HELO', options(hash)]
      // ['HELO', {'nonce' => nonce, 'auth' => user_auth_salt/empty string, 'keepalive' => true/false}].to_msgpack
      MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
      try {
        packer.packArrayHeader(2).packString("HELO").packMapHeader(3).packString("nonce")
            .packBinaryHeader(16).writePayload(nonce).packString("auth").packBinaryHeader(16)
            .writePayload(userAuth).packString("keepalive").packBoolean(true);
      } catch (IOException e) {
        logger.error("Failed to pack HELO message", e);
      }

      return packer.toMessageBuffer().sliceAsByteBuffer();
    }

    protected ByteBuffer generatePong(CheckPingResult checkPingResult) {
      // [
      //   'PONG',
      //   bool(authentication result),
      //   'reason if authentication failed',
      //   self_hostname,
      //   sha512_hex(salt + self_hostname + nonce + sharedkey)
      // ]
      MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
      try {
        if (checkPingResult.isSucceeded()) {
          MessageDigest md = MessageDigest.getInstance("SHA-512");
          md.update(checkPingResult.getSharedKeySalt().getBytes());
          md.update(security.getSelfHostname().getBytes());
          md.update(nonce);
          md.update(checkPingResult.getSharedKey().getBytes());
          packer.packArrayHeader(5).packString("PONG").packBoolean(checkPingResult.isSucceeded())
              .packString("").packString(security.getSelfHostname())
              .packString(generateHexString(md.digest()));
        } else {
          packer.packArrayHeader(5).packString("PONG").packBoolean(checkPingResult.isSucceeded())
              .packString(checkPingResult.getReason()).packString("").packString("");
        }
      } catch (IOException e) {
        logger.error("Failed to pack PONG message", e);
      } catch (NoSuchAlgorithmException e) {
        logger.error(e.getMessage(), e);
      }

      return packer.toMessageBuffer().sliceAsByteBuffer();
    }

    private String generateHexString(final byte[] digest) {
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    }
}
