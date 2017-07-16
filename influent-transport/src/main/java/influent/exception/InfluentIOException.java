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

package influent.exception;

/**
 * IO error.
 */
public final class InfluentIOException extends RuntimeException {
  /**
   * Constructs a new {@code InfluentIOException}.
   *
   * @param message the error message
   * @param cause the cause
   */
  public InfluentIOException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new {@code InfluentIOException}.
   *
   * @param message the error message
   */
  public InfluentIOException(final String message) {
    super(message);
  }

  /**
   * Constructs a new {@code InfluentIOException}.
   */
  public InfluentIOException() {
    super();
  }
}
