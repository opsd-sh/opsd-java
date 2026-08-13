package sh.opsd;

/** Raised when an API credential is empty or malformed. */
public final class InvalidApiCredentialException extends OpsdException {
  /** Creates the exception. */
  public InvalidApiCredentialException() {
    super("invalid API credential");
  }
}
