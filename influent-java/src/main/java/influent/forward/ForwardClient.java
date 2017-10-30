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

  public String getHost() {
    return host;
  }

  public String getNetwork() {
    return network;
  }

  public String getSharedKey() {
    return sharedKey;
  }

  public List<String> getUsernames() {
    return usernames;
  }

  public static class Builder {
    private String host = null;
    private String network = null;
    private String sharedKey = null;
    private List<String> usernames = new ArrayList<>();

    private Builder() {
    }

    /**
     * Create new ForwardClient.Builder with given host
     *
     * @param host The IP address or host name of the client
     * @return new builder
     */
    public static Builder ofHost(String host) {
      Builder builder = new Builder();
      builder.host = host;
      return builder;
    }

    /**
     * Create new ForwardClient.Builder with given network
     *
     * @param network Network address specification
     * @return new builder
     */
    public static Builder ofNetwork(String network) {
      Builder builder = new Builder();
      builder.network = network;
      return builder;
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
