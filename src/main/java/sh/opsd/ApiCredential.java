package sh.opsd;

/** A secret used to authenticate requests to the public Opsd API. */
public final class ApiCredential {
  private final String authorizationValue;

  /**
   * Creates a validated bearer credential.
   *
   * @param secret an OAuth access token or API key
   * @throws InvalidApiCredentialException if the secret cannot be used in an HTTP header
   */
  public ApiCredential(String secret) {
    if (!isVisibleAscii(secret)) {
      throw new InvalidApiCredentialException();
    }
    authorizationValue = "Bearer " + secret;
  }

  String authorizationValue() {
    return authorizationValue;
  }

  @Override
  public String toString() {
    return "ApiCredential([REDACTED])";
  }

  private static boolean isVisibleAscii(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < 0x21 || character > 0x7e) {
        return false;
      }
    }
    return true;
  }
}
