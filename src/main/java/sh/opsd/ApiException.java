package sh.opsd;

import java.util.Objects;

/** Raised when the server returns structured problem details. */
public final class ApiException extends OpsdException {
  private final int statusCode;
  private final ProblemDetails problem;

  /** Creates the exception. */
  public ApiException(int statusCode, ProblemDetails problem) {
    super("API request failed: " + Objects.requireNonNull(problem, "problem"));
    this.statusCode = statusCode;
    this.problem = problem;
  }

  /** Returns the HTTP response status code. */
  public int statusCode() {
    return statusCode;
  }

  /** Returns the structured API problem. */
  public ProblemDetails problem() {
    return problem;
  }
}
