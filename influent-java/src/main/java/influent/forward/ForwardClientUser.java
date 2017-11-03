package influent.forward;

public class ForwardClientUser {
  private final String username;
  private final String password;

  public ForwardClientUser(final String username, final String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
