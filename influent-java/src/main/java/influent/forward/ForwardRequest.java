package influent.forward;

import java.util.Objects;

import influent.EventStream;

final class ForwardRequest {
  private final EventStream stream;
  private final ForwardOption option;

  private ForwardRequest(final EventStream stream, final ForwardOption option) {
    this.stream = stream;
    this.option = option;
  }

  static ForwardRequest of(final EventStream stream, final ForwardOption option) {
    return new ForwardRequest(stream, option);
  }

  EventStream getStream() {
    return stream;
  }

  ForwardOption getOption() {
    return option;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForwardRequest that = (ForwardRequest) o;
    return Objects.equals(getStream(), that.getStream()) &&
        Objects.equals(getOption(), that.getOption());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getStream(), getOption());
  }

  @Override
  public String toString() {
    return "ForwardRequest(" + getStream() + "," + getOption() + ")";
  }
}
