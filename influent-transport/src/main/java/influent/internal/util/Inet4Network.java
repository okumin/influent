package influent.internal.util;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Inet4Network implements InetNetwork {
  private final static byte[] MAX = {
      (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
  };
  private final static BigInteger MAX_VALUE = new BigInteger(1, MAX);
  private InetAddress network;
  private InetAddress netmask;

  public Inet4Network(InetAddress base, int netmask) {
    this.netmask = generateNetmask(netmask);
    this.network = InetNetwork.maskIP(base, this.netmask);
  }

  @Override
  public boolean contains(InetAddress address) {
    if (!(address instanceof Inet4Address)) {
      return false;
    }
    return network.equals(InetNetwork.maskIP(address, netmask));
  }

  private InetAddress generateNetmask(int mask) {
    int bits = 32 - mask;
    // int netmask = 0xFFFFFFFF - ((1 << bits) - 1);
    BigInteger netmask = MAX_VALUE.subtract(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
    byte[] address = new byte[4];
    for (int i = 0; i < 4; i++) {
      int b = 32 - 8 * (i + 1);
      address[i] = (netmask.shiftRight(b).and(BigInteger.valueOf(0xFF))).byteValue();
    }

    try {
      return InetAddress.getByAddress(address);
    } catch (UnknownHostException e) {
      return null;
    }
  }
}
