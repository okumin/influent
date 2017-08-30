package influent.forward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForwardSecurity {
  private String selfHostname = null;
  private String sharedKey = null;
  private boolean userAuthEnabled = false;
  private boolean anonymousSourceAllowed = true;

  private List<ForwardClient> clients = new ArrayList<>();
  private Map<String, String> users = new HashMap<>();

  public ForwardSecurity(String selfHostname,
                         String sharedKey,
                         boolean userAuthEnabled,
                         boolean anonymousSourceAllowed,
                         List<ForwardClient> clients,
                         Map<String, String> users) {
    this.selfHostname = selfHostname;
    this.sharedKey = sharedKey;
    this.userAuthEnabled = userAuthEnabled;
    this.anonymousSourceAllowed = anonymousSourceAllowed;
    this.clients.addAll(clients);
    this.users.putAll(users);
  }

  public static class Builder {
    private String selfHostname = null;
    private String sharedKey = null;
    private boolean userAuthEnabled = false;
    private boolean anonymousSourceAllowed = true;

    private List<ForwardClient> clients = new ArrayList<>();
    private Map<String, String> users = new HashMap<>();

    /**
     * Set the hostname
     *
     * @param selfHostname The hostname
     * @return this builder
     */
    public Builder selfHostname(String selfHostname) {
      this.selfHostname = selfHostname;
      return this;
    }

    /**
     * Set shared key for authentication
     *
     * @param sharedKey Shared key for authentication
     * @return this builder
     */
    public Builder sharedKey(String sharedKey) {
      this.sharedKey = sharedKey;
      return this;
    }

    /**
     * Use user base authentication or not
     *
     * @param userAuthEnabled If true, use user based authentication
     * @return this builder
     */
    public Builder enableUserAuth(boolean userAuthEnabled) {
      this.userAuthEnabled = userAuthEnabled;
      return this;
    }

    /**
     * Allow anonymous source or not. Clients are required if not allowed anonymous source.
     *
     * @param anonymousSourceAllowed If true, allow anonymous source. Otherwise clients are required.
     * @return this builder
     */
    public Builder allowAnonymousSource(boolean anonymousSourceAllowed) {
      this.anonymousSourceAllowed = anonymousSourceAllowed;
      return this;
    }

    /**
     * Add username and passowrd for user based authentication
     *
     * @param username The username for authentication
     * @param password The password for authentication
     * @return this builder
     */
    public Builder addUser(final String username, final String password) {
      users.put(username, password);
      return this;
    }

    /**
     * Add client ip/network authentication & per_host shared key
     *
     * @param client The ForwardClient
     * @return this builder
     */
    public Builder addClient(ForwardClient client) {
      clients.add(client);
      return this;
    }

    public ForwardSecurity build() {
      return new ForwardSecurity(
          selfHostname,
          sharedKey,
          userAuthEnabled,
          anonymousSourceAllowed,
          clients,
          users
      );
    }
  }
}
