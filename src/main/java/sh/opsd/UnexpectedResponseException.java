package sh.opsd;

/** Raised when an HTTP response cannot be decoded as expected. */
public final class UnexpectedResponseException extends OpsdException {
  private final int statusCode;
  private final String body;

  /** Creates the exception. */
  public UnexpectedResponseException(int statusCode, String body, Throwable cause) {
    super("API request returned an unexpected response: " + body, cause);
    this.statusCode = statusCode;
    this.body = body;
  }

  /** Returns the HTTP response status code. */
  public int statusCode() {
    return statusCode;
  }

  /** Returns the unrecognized response body. */
  public String body() {
    return body;
  }
}
