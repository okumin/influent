package influent.forward;

import influent.internal.util.InetNetwork;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class ForwardClientNode {
  private InetAddress sourceAddress = null;
  private InetNetwork network = null;
  private String sharedKey = null;
  private List<String> usernames = null;

  public ForwardClientNode(ForwardClient client, String globalSharedKey) {
    if (client.getHost() != null) {
      try {
        sourceAddress = InetAddress.getByName(client.getHost());
      } catch (UnknownHostException e) {
        e.printStackTrace();
        // TODO throw exception
      }
    }
    if (client.getNetwork() != null) {
      network = InetNetwork.getBySpec(client.getNetwork());
    }
    sharedKey = client.getSharedKey() == null ? globalSharedKey : client.getSharedKey();
    usernames = client.getUsernames();
  }

  public boolean isMatched(InetAddress remoteAddress) {
    if (sourceAddress != null) {
      return sourceAddress.equals(remoteAddress);
    } else {
      return network.contains(remoteAddress);
    }
  }

  public String getSharedKey() {
    return sharedKey;
  }

  public List<String> getUsernames() {
    return usernames;
  }
}
