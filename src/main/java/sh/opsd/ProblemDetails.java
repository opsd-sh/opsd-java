package sh.opsd;

import java.util.Objects;

/** Structured details returned for an Opsd API error. */
public record ProblemDetails(
    String type, String title, int status, String detail, String category) {
  /** Creates structured problem details. */
  public ProblemDetails {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(category, "category");
  }

  @Override
  public String toString() {
    return detail;
  }
}
