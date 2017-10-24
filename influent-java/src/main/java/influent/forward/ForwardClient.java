package influent.forward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client ip/network authentication & per_host shared key
 */
public class ForwardClient {
  private String host = null;
  private String network = null;
  private String sharedKey = null;
  private List<String> usernames = new ArrayList<>();

  public ForwardClient(String host,
                       String network,
                       String sharedKey,
                       List<String> usernames) {
    this.host = host;
    this.network = network;
    this.sharedKey = sharedKey;
    this.usernames = usernames;
  }

  public static class Builder {
    private String host;
    private String network;
    private String sharedKey;
    private List<String> usernames;

    /**
     * Set the IP address or host name of the client
     *
     * @param host The IP address or host name of the client
     * @return this builder
     */
    public Builder host(String host) {
      this.host = host;
      return this;
    }

    /**
     * Set network address specification
     *
     * @param network Network address specification
     * @return this builder
     */
    public Builder network(String network) {
      this.network = network;
      return this;
    }

    /**
     * Set shared key per client
     *
     * @param sharedKey Shared key per client
     * @return this builder
     */
    public Builder sharedKey(String sharedKey) {
      this.sharedKey = sharedKey;
      return this;
    }

    /**
     * Set usernames to authenticate client
     *
     * @param usernames Usernames to authenticate client
     * @return this builder
     */
    public Builder usernames(String... usernames) {
      Collections.addAll(this.usernames, usernames);
      return this;
    }

    public ForwardClient build() {
      return new ForwardClient(host, network, sharedKey, usernames);
    }
  }
}
