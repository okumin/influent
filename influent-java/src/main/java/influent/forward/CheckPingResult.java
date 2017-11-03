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
