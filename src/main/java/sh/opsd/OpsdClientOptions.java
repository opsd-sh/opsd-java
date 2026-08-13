package sh.opsd;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/** Configuration for {@link OpsdClient}. */
public final class OpsdClientOptions {
  /** The production Opsd API base URI. */
  public static final URI PRODUCTION_BASE_URI = URI.create("https://api.opsd.sh/v1/");

  private final URI baseUri;
  private final HttpClient httpClient;
  private final Duration requestTimeout;

  private OpsdClientOptions(Builder builder) {
    baseUri = builder.baseUri;
    httpClient = builder.httpClient;
    requestTimeout = builder.requestTimeout;
  }

  /** Returns a builder initialized with the production defaults. */
  public static Builder builder() {
    return new Builder();
  }

  URI baseUri() {
    return baseUri;
  }

  HttpClient httpClient() {
    return httpClient;
  }

  Duration requestTimeout() {
    return requestTimeout;
  }

  /** Builds client options. */
  public static final class Builder {
    private URI baseUri = PRODUCTION_BASE_URI;
    private HttpClient httpClient = HttpClient.newHttpClient();
    private Duration requestTimeout = Duration.ofSeconds(100);

    private Builder() {}

    /** Sets the API base URI. */
    public Builder baseUri(URI value) {
      baseUri = Objects.requireNonNull(value, "baseUri");
      return this;
    }

    /** Sets the HTTP client used to send requests. */
    public Builder httpClient(HttpClient value) {
      httpClient = Objects.requireNonNull(value, "httpClient");
      return this;
    }

    /** Sets the timeout applied to each API request. */
    public Builder requestTimeout(Duration value) {
      Objects.requireNonNull(value, "requestTimeout");
      if (value.isZero() || value.isNegative()) {
        throw new IllegalArgumentException("requestTimeout must be positive");
      }
      requestTimeout = value;
      return this;
    }

    /** Returns immutable client options. */
    public OpsdClientOptions build() {
      return new OpsdClientOptions(this);
    }
  }
}
