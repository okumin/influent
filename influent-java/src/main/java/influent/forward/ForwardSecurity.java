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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ForwardSecurity {
  private String selfHostname = null;
  private String sharedKey = null;
  private boolean userAuthEnabled = false;
  private boolean anonymousSourceAllowed = true;
  private boolean enabled;

  private List<ForwardClient> clients = new ArrayList<>();
  private List<ForwardClientUser> users = new ArrayList<>();
  private List<ForwardClientNode> nodes = new ArrayList<>();

  public ForwardSecurity() {
    this.enabled = false;
  }

  public ForwardSecurity(String selfHostname, String sharedKey, boolean userAuthEnabled,
      boolean anonymousSourceAllowed, List<ForwardClient> clients, List<ForwardClientUser> users) {
    this.selfHostname = selfHostname;
    this.sharedKey = sharedKey;
    this.userAuthEnabled = userAuthEnabled;
    this.anonymousSourceAllowed = anonymousSourceAllowed;
    this.clients.addAll(clients);
    this.users.addAll(users);
    this.enabled = true;

    for (ForwardClient client : this.clients) {
      nodes.add(new ForwardClientNode(client, this.sharedKey));
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isAnonymousSourceAllowed() {
    return anonymousSourceAllowed;
  }

  public boolean isUserAuthEnabled() {
    return userAuthEnabled;
  }

  public Optional<ForwardClientNode> findNode(InetAddress remoteAddress) {
    return nodes
        .stream()
        .filter(n -> n.isMatched(remoteAddress))
        .findFirst();
  }

  public String getSelfHostname() {
    return selfHostname;
  }

  public String getSharedKey() {
    return sharedKey;
  }

  public List<ForwardClientUser> findAuthenticateUsers(ForwardClientNode node, String username) {
    if (node == null || node.getUsernames().isEmpty()) {
      return users.stream()
          .filter(user -> user.getUsername().equals(username))
          .collect(Collectors.toList());
    } else {
      return users.stream()
          .filter(user -> node.getUsernames().contains(username) && user.getUsername().equals(username))
          .collect(Collectors.toList());
    }
  }

  public static class Builder {
    private String selfHostname = null;
    private String sharedKey = null;
    private boolean userAuthEnabled = false;
    private boolean anonymousSourceAllowed = true;

    private List<ForwardClient> clients = new ArrayList<>();
    private List<ForwardClientUser> users = new ArrayList<>();

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
      users.add(new ForwardClientUser(username, password));
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
      // TODO Check required parameters
      return new ForwardSecurity(selfHostname, sharedKey, userAuthEnabled, anonymousSourceAllowed,
          clients, users);
    }
  }
}
