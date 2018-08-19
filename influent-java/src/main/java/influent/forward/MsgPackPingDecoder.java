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

package influent.forward;

import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class MsgPackPingDecoder {
  private static final Logger logger = LoggerFactory.getLogger(MsgPackPingDecoder.class);

  private final ForwardSecurity security;
  private final ForwardClientNode node;
  private final byte[] nonce;
  private final byte[] userAuth;

  public MsgPackPingDecoder(
      ForwardSecurity security, ForwardClientNode node, byte[] nonce, byte[] userAuth) {
    this.security = security;
    this.node = node;
    this.nonce = nonce;
    this.userAuth = userAuth;
  }

  public CheckPingResult decode(ImmutableValue value) {
    logger.debug("decoding ping {}", value);
    if (!value.isArrayValue()) {
      error("A ping message must be an array", value);
    }
    ImmutableArrayValue arrayValue = value.asArrayValue();
    if (arrayValue.size() != 6) {
      error("A ping message must have 6 elements", value);
    }
    String ping = decodeString(arrayValue.get(0));
    if (!ping.equals(("PING"))) {
      error("Invalid ping message", value);
    }
    String clientHostname = decodeString(arrayValue.get(1));
    String sharedKeySalt = decodeString(arrayValue.get(2)); // TODO Support both String and byte[]
    String sharedKeyHexDigest = decodeString(arrayValue.get(3));
    String username = decodeString(arrayValue.get(4));
    String passwordDigest = decodeString(arrayValue.get(5));

    if (node == null && !security.isAnonymousSourceAllowed()) {
      // FIXME add remote address to message
      String message = "Anonymous client disallowed.";
      logger.warn(message);
      return CheckPingResult.failure(message);
    }

    String sharedKey = null;
    try {
      sharedKey = node != null ? node.getSharedKey() : security.getSharedKey();
      MessageDigest md = MessageDigest.getInstance("SHA-512");
      md.update(sharedKeySalt.getBytes());
      md.update(clientHostname.getBytes());
      md.update(nonce);
      md.update(sharedKey.getBytes());
      String serverSideDigest = generateHexString(md.digest());
      if (!sharedKeyHexDigest.equals(serverSideDigest)) {
        // FIXME Add remote address to log
        logger.warn("Shared key mismatch: {}", clientHostname);
        return CheckPingResult.failure("Shared key mismatch");
      }

      if (security.isUserAuthEnabled()) {
        boolean userAuthenticationSucceeded =
            security
                .findAuthenticateUsers(node, username)
                .stream()
                .anyMatch(
                    user -> {
                      md.reset();
                      md.update(userAuth);
                      md.update(username.getBytes());
                      md.update(user.getPassword().getBytes());
                      String serverSidePasswordDigest = generateHexString(md.digest());
                      return passwordDigest.equals(serverSidePasswordDigest);
                    });
        if (!userAuthenticationSucceeded) {
          // FIXME Add remote address to log
          logger.info("Authentication failed: hostname={}, username={}", clientHostname, username);
          return CheckPingResult.failure("username/password mismatch");
        }
      }
    } catch (NoSuchAlgorithmException e) {
      error(e.getMessage(), value, e);
    }

    return CheckPingResult.success(sharedKeySalt, sharedKey);
  }

  private String decodeString(final Value value) {
    return value == null ? null : value.asStringValue().asString();
  }

  private byte[] decodeByteArray(final Value value) {
    return value == null ? null : value.asBinaryValue().asByteArray();
  }

  private String generateHexString(final byte[] digest) {
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private IllegalArgumentException error(final String message, final Value value) {
    throw new IllegalArgumentException(message + " value = " + value);
  }

  private IllegalArgumentException error(
      final String message, final Value value, final Throwable cause) {
    throw new IllegalArgumentException(message + " value = " + value, cause);
  }
}
