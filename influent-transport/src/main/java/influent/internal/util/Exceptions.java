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

package influent.internal.util;

import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for exceptions.
 *
 * <p>This is expected to be used in only Influent project.
 */
public final class Exceptions {
  /**
   * A callable block.
   *
   * <p>This is the same as {@code Runnable} except that {@code Block#run} may throw some {@code
   * Exception}.
   */
  @FunctionalInterface
  public interface Block {
    void run() throws Exception;
  }

  /** Boolean specialized callable. */
  @FunctionalInterface
  public interface BooleanCallable {
    boolean call() throws Exception;
  }

  private static final Logger logger = LoggerFactory.getLogger(Exceptions.class);

  private Exceptions() {
    throw new AssertionError();
  }

  /**
   * Executes the given processing and discards the error when some error occurs.
   *
   * @param f the processing
   * @param messageOnError the error message to be logged on an error
   */
  public static void ignore(final Block f, final String messageOnError) {
    try {
      f.run();
    } catch (final Exception e) {
      logger.error(messageOnError, e);
    }
  }

  /**
   * Executes the given processing and return null if the processing throws an exception.
   *
   * @param f the processing
   * @param <T> the type of the {@code Callable}
   * @return the return value of {@code f} or null when {@code f} fails
   */
  public static <T> T orNull(final Callable<T> f) {
    try {
      return f.call();
    } catch (final Exception e) {
      return null;
    }
  }

  /**
   * Executes the given processing and return false if the processing throws an exception.
   *
   * @param f the processing
   * @return true if {@code f} returns true
   */
  public static boolean orFalse(final BooleanCallable f) {
    try {
      return f.call();
    } catch (final Exception e) {
      return false;
    }
  }
}
