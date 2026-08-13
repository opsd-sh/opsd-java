package sh.opsd;

import java.util.Objects;

/** The fields used to create an Opsd test user. */
public record CreateUserRequest(String name, String email) {
  /** Creates a user request. */
  public CreateUserRequest {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(email, "email");
  }
}
