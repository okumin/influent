package influent.internal.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface InetNetwork {
  static InetNetwork getBySpec(String spec) {
    String[] addressComponents = spec.split("/", 2);
    if (addressComponents.length != 2) {
      throw new IllegalArgumentException("Invalid network: " + spec);
    }
    try {
      int netmask = Integer.parseInt(addressComponents[1]);
      InetAddress base = InetAddress.getByName(addressComponents[0]);
      if (base.getAddress().length == 16) {
        return new Inet6Network(base, netmask);
      } else {
        return new Inet4Network(base, netmask);
      }
    } catch (NumberFormatException | UnknownHostException e) {
      throw new IllegalArgumentException("Invalid network: " + spec, e);
    }
  }

  static InetAddress maskIP(InetAddress ip, InetAddress netmask) {
    return maskIP(ip.getAddress(), netmask.getAddress());
  }

  static InetAddress maskIP(byte[] ip, byte[] mask) {
    if (ip.length != mask.length) {
      throw new IllegalArgumentException("IP address and mask must be of the same length.");
    }
    byte[] masked = new byte[ip.length];

    for (int i = 0; i < ip.length; i++) {
      masked[i] = (byte) (ip[i] & mask[i]);
    }

    try {
      return InetAddress.getByAddress(masked);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  boolean contains(InetAddress address);
}
