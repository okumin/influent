package influent.forward;

public class CheckPingResult {
  private final boolean succeeded;
  private final String sharedKeySalt;
  private final String sharedKey;
  private final String reason;

  public static CheckPingResult success(String sharedKeySalt, String sharedKey) {
    return new CheckPingResult(true, sharedKeySalt, sharedKey, null);
  }

  public static CheckPingResult failure(String reason) {
    return new CheckPingResult(false, null, null, reason);
  }

  public CheckPingResult(boolean succeeded, String sharedKeySalt, String sharedKey, String reason) {
    this.succeeded = succeeded;
    this.sharedKeySalt = sharedKeySalt;
    this.sharedKey = sharedKey;
    this.reason = reason;
  }

  public boolean isSucceeded() {
    return succeeded;
  }

  public String getSharedKeySalt() {
    return sharedKeySalt;
  }

  public String getSharedKey() {
    return sharedKey;
  }

  public String getReason() {
    return reason;
  }
}
