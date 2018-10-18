package example;

class Environment {
  private Environment() {
    throw new AssertionError();
  }

  static String get(final String name, final String defaultValue) {
    final String value = System.getenv(name);
    return value != null ? value : defaultValue;
  }

  static long getLong(final String name, final long defaultValue) {
    final String value = System.getenv(name);
    return value != null ? Long.valueOf(value) : defaultValue;
  }

  static boolean getBoolean(final String name, final boolean defaultValue) {
    final String value = System.getenv(name);
    return value != null ? Boolean.valueOf(value) : defaultValue;
  }
}
