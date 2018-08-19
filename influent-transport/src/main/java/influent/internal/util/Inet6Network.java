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

package influent.internal.util;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Inet6Network implements InetNetwork {
  private static final byte[] MAX = {
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff
  };
  private static final BigInteger MAX_VALUE = new BigInteger(1, MAX);
  private InetAddress network;
  private InetAddress netmask;

  public Inet6Network(InetAddress base, int netmask) {
    this.netmask = generateNetmask(netmask);
    this.network = InetNetwork.maskIP(base, this.netmask);
  }

  @Override
  public boolean contains(InetAddress address) {
    return network.equals(InetNetwork.maskIP(address, netmask));
  }

  private InetAddress generateNetmask(int mask) {
    int bits = 128 - mask;
    // 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF - (1 << bits) -1
    BigInteger netmask =
        MAX_VALUE.subtract(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
    byte[] address = new byte[16];
    for (int i = 0; i < 16; i++) {
      int b = 128 - 8 * (i + 1);
      address[i] = (netmask.shiftRight(b).and(BigInteger.valueOf(0xFF))).byteValue();
    }

    try {
      return InetAddress.getByAddress(address);
    } catch (UnknownHostException e) {
      return null;
    }
  }
}
