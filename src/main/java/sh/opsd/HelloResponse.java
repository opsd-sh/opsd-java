package sh.opsd;

import java.util.Objects;

/** A response from an Opsd hello endpoint. */
public record HelloResponse(String message) {
  /** Creates a hello response. */
  public HelloResponse {
    Objects.requireNonNull(message, "message");
  }
}
