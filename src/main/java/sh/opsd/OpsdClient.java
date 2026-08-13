package sh.opsd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/** An asynchronous client for the public Opsd API. */
public final class OpsdClient {
  private static final String ACCEPT = "application/json, application/problem+json";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ApiCredential credential;
  private final URI baseUri;
  private final HttpClient httpClient;
  private final java.time.Duration requestTimeout;

  /** Creates a client for the production Opsd API. */
  public OpsdClient(ApiCredential credential) {
    this(credential, OpsdClientOptions.builder().build());
  }

  /** Creates a client with explicit options. */
  public OpsdClient(ApiCredential credential, OpsdClientOptions options) {
    this.credential = Objects.requireNonNull(credential, "credential");
    Objects.requireNonNull(options, "options");
    baseUri = normalizeBaseUri(options.baseUri());
    httpClient = options.httpClient();
    requestTimeout = options.requestTimeout();
  }

  /** Returns the normalized API base URI. */
  public URI baseUri() {
    return baseUri;
  }

  /** Calls the unauthenticated hello-world sandbox route. */
  public CompletableFuture<HelloResponse> helloWorld() {
    return send("GET", "hello/world", null, OpsdClient::decodeHelloResponse);
  }

  /** Calls the authenticated application hello route. */
  public CompletableFuture<HelloResponse> helloApplication() {
    return send("GET", "hello/application", null, OpsdClient::decodeHelloResponse);
  }

  /** Lists test users. */
  public CompletableFuture<List<User>> listUsers() {
    return send("GET", "test/users", null, OpsdClient::decodeUsers);
  }

  /** Creates a test user. */
  public CompletableFuture<User> createUser(CreateUserRequest request) {
    Objects.requireNonNull(request, "request");
    String body;
    try {
      body = JSON.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("could not encode the user request", exception);
    }
    return send("POST", "test/users", body, OpsdClient::decodeUser);
  }

  @Override
  public String toString() {
    return "OpsdClient(baseUri=" + baseUri + ", credential=[REDACTED])";
  }

  private <T> CompletableFuture<T> send(
      String method, String path, String body, Function<JsonNode, T> decoder) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(requestTimeout)
            .header("Accept", ACCEPT)
            .header("Authorization", credential.authorizationValue());

    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }

    return httpClient
        .sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        .handle(
            (response, failure) -> {
              if (failure != null) {
                Throwable cause = unwrap(failure);
                throw new CompletionException(
                    new TransportException("request failed: " + cause.getMessage(), cause));
              }
              return decode(response, decoder);
            });
  }

  private static <T> T decode(HttpResponse<String> response, Function<JsonNode, T> decoder) {
    JsonNode value;
    try {
      value = JSON.readTree(response.body());
      if (value == null) {
        throw new IllegalArgumentException("expected a JSON value");
      }
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw unexpected(response, exception);
    }

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      try {
        return decoder.apply(value);
      } catch (IllegalArgumentException | NullPointerException exception) {
        throw unexpected(response, exception);
      }
    }

    ProblemDetails problem;
    try {
      problem = decodeProblemDetails(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw unexpected(response, exception);
    }
    throw new ApiException(response.statusCode(), problem);
  }

  private static UnexpectedResponseException unexpected(
      HttpResponse<String> response, Throwable cause) {
    return new UnexpectedResponseException(response.statusCode(), response.body(), cause);
  }

  private static HelloResponse decodeHelloResponse(JsonNode value) {
    return new HelloResponse(requiredText(value, "message"));
  }

  private static List<User> decodeUsers(JsonNode value) {
    if (!value.isArray()) {
      throw new IllegalArgumentException("expected a JSON array");
    }
    List<User> users = new ArrayList<>();
    value.forEach(item -> users.add(decodeUser(item)));
    return List.copyOf(users);
  }

  private static User decodeUser(JsonNode value) {
    return new User(
        requiredInt(value, "id"), requiredText(value, "name"), requiredText(value, "email"));
  }

  private static ProblemDetails decodeProblemDetails(JsonNode value) {
    return new ProblemDetails(
        requiredText(value, "type"),
        requiredText(value, "title"),
        requiredInt(value, "status"),
        requiredText(value, "detail"),
        requiredText(value, "category"));
  }

  private static String requiredText(JsonNode value, String field) {
    JsonNode property = value.isObject() ? value.get(field) : null;
    if (property == null || !property.isTextual()) {
      throw new IllegalArgumentException("expected `" + field + "` to be a string");
    }
    return property.textValue();
  }

  private static int requiredInt(JsonNode value, String field) {
    JsonNode property = value.isObject() ? value.get(field) : null;
    if (property == null || !property.isIntegralNumber() || !property.canConvertToInt()) {
      throw new IllegalArgumentException("expected `" + field + "` to be an integer");
    }
    return property.intValue();
  }

  private static URI normalizeBaseUri(URI value) {
    String scheme = value.getScheme();
    if (!value.isAbsolute()
        || value.isOpaque()
        || value.getHost() == null
        || scheme == null
        || !(scheme.toLowerCase(Locale.ROOT).equals("http")
            || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
      throw new InvalidBaseUrlException(value.toString(), "expected an absolute HTTP or HTTPS URI");
    }
    if (value.getRawQuery() != null || value.getRawFragment() != null) {
      throw new InvalidBaseUrlException(value.toString(), "queries and fragments are not allowed");
    }
    if (value.getPath().endsWith("/")) {
      return value;
    }
    return URI.create(value + "/");
  }

  private static Throwable unwrap(Throwable failure) {
    if (failure instanceof CompletionException && failure.getCause() != null) {
      return failure.getCause();
    }
    return failure;
  }
}
