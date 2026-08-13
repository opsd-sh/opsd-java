package sh.opsd;

import java.util.Objects;

/** An Opsd test user. */
public record User(int id, String name, String email) {
  /** Creates a user. */
  public User {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(email, "email");
  }
}
