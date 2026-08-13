package sh.opsd;

/** Base class for errors raised by the Opsd client. */
public abstract class OpsdException extends RuntimeException {
  protected OpsdException(String message) {
    super(message);
  }

  protected OpsdException(String message, Throwable cause) {
    super(message, cause);
  }
}
