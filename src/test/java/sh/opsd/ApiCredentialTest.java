package sh.opsd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

final class ApiCredentialTest {
  @Test
  void redactsTheSecret() {
    ApiCredential credential = new ApiCredential("opsd_key_secret");

    assertEquals("ApiCredential([REDACTED])", credential.toString());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"contains space", "contains\nnewline", "contains\0null", "\u007f", "£"})
  void rejectsInvalidSecrets(String secret) {
    assertThrows(InvalidApiCredentialException.class, () -> new ApiCredential(secret));
  }
}
