package sh.opsd;

/** Raised when an HTTP request fails before a response is received. */
public final class TransportException extends OpsdException {
  /** Creates the exception. */
  public TransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
