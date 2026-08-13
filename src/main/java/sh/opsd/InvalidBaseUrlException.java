package sh.opsd;

/** Raised when a client base URI is not valid. */
public final class InvalidBaseUrlException extends OpsdException {
  private final String baseUri;

  /** Creates the exception. */
  public InvalidBaseUrlException(String baseUri, String message) {
    super("invalid base URI `" + baseUri + "`: " + message);
    this.baseUri = baseUri;
  }

  /** Returns the rejected base URI. */
  public String baseUri() {
    return baseUri;
  }
}
