package sh.opsd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class OpsdClientTest {
  @Test
  void defaultsToProductionAndRedactsTheCredential() {
    OpsdClient client = new OpsdClient(new ApiCredential("secret-access-token"));

    assertEquals(URI.create("https://api.opsd.sh/v1/"), client.baseUri());
    assertFalse(client.toString().contains("secret-access-token"));
  }

  @Test
  void normalizesAnExistingBasePath() {
    OpsdClient client =
        new OpsdClient(
            new ApiCredential("token"),
            OpsdClientOptions.builder().baseUri(URI.create("https://example.test/v1")).build());

    assertEquals(URI.create("https://example.test/v1/"), client.baseUri());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"relative/path", "ftp://example.test/v1", "https://example.test/v1?tenant=one"})
  void rejectsInvalidBaseUris(String baseUri) {
    OpsdClientOptions options = OpsdClientOptions.builder().baseUri(URI.create(baseUri)).build();

    assertThrows(
        InvalidBaseUrlException.class, () -> new OpsdClient(new ApiCredential("token"), options));
  }

  @Test
  void authenticatesAndDecodesTheHelloEndpoint() throws IOException {
    try (TestServer server = new TestServer()) {
      server.respondWith(200, "{\"message\":\"hello\"}");
      OpsdClient client = clientFor(server);

      assertEquals(new HelloResponse("hello"), client.helloWorld().join());
      CapturedRequest request = server.requests().get(0);
      assertEquals("GET", request.method());
      assertEquals("/v1/hello/world", request.path());
      assertEquals("Bearer secret", request.authorization());
      assertEquals("application/json, application/problem+json", request.accept());
    }
  }

  @Test
  void usesTheExpectedEndpointPathsAndModels() throws IOException {
    try (TestServer server = new TestServer()) {
      server.respondUsing(
          request -> {
            if (request.getRequestURI().getPath().endsWith("/hello/application")) {
              return new Response(200, "{\"message\":\"application\"}");
            }
            if (request.getRequestMethod().equals("GET")) {
              return new Response(
                  200, "[{\"id\":1,\"name\":\"Ada\",\"email\":\"ada@example.test\"}]");
            }
            return new Response(
                201, "{\"id\":2,\"name\":\"Grace\",\"email\":\"grace@example.test\"}");
          });
      OpsdClient client = clientFor(server);

      assertEquals(new HelloResponse("application"), client.helloApplication().join());
      assertEquals(List.of(new User(1, "Ada", "ada@example.test")), client.listUsers().join());
      assertEquals(
          new User(2, "Grace", "grace@example.test"),
          client.createUser(new CreateUserRequest("Grace", "grace@example.test")).join());

      assertEquals(
          List.of("/v1/hello/application", "/v1/test/users", "/v1/test/users"),
          server.requests().stream().map(CapturedRequest::path).toList());
      CapturedRequest create = server.requests().get(2);
      assertEquals("POST", create.method());
      assertEquals("application/json", create.contentType());
      assertEquals("{\"name\":\"Grace\",\"email\":\"grace@example.test\"}", create.body());
    }
  }

  @Test
  void exposesProblemDetailsOnApiErrors() throws IOException {
    try (TestServer server = new TestServer()) {
      server.respondWith(
          404,
          """
          {"type":"https://api.opsd.sh/problems/not-found","title":"Not Found",
           "status":404,"detail":"no route found","category":"request"}
          """);
      OpsdClient client = clientFor(server);

      CompletionException completion =
          assertThrows(CompletionException.class, () -> client.helloWorld().join());
      ApiException error = assertInstanceOf(ApiException.class, completion.getCause());
      assertEquals(404, error.statusCode());
      assertEquals(
          new ProblemDetails(
              "https://api.opsd.sh/problems/not-found",
              "Not Found",
              404,
              "no route found",
              "request"),
          error.problem());
    }
  }

  @Test
  void preservesUnrecognizedResponses() throws IOException {
    try (TestServer server = new TestServer()) {
      server.respondWith(502, "bad gateway");
      OpsdClient client = clientFor(server);

      CompletionException completion =
          assertThrows(CompletionException.class, () -> client.helloWorld().join());
      UnexpectedResponseException error =
          assertInstanceOf(UnexpectedResponseException.class, completion.getCause());
      assertEquals(502, error.statusCode());
      assertEquals("bad gateway", error.body());
    }
  }

  @Test
  void wrapsTransportErrors() throws IOException {
    int unavailablePort;
    try (ServerSocket socket = new ServerSocket(0)) {
      unavailablePort = socket.getLocalPort();
    }
    OpsdClient client =
        new OpsdClient(
            new ApiCredential("secret"),
            OpsdClientOptions.builder()
                .baseUri(URI.create("http://127.0.0.1:" + unavailablePort + "/v1/"))
                .build());

    CompletionException completion =
        assertThrows(CompletionException.class, () -> client.helloWorld().join());
    assertInstanceOf(TransportException.class, completion.getCause());
  }

  private static OpsdClient clientFor(TestServer server) {
    return new OpsdClient(
        new ApiCredential("secret"), OpsdClientOptions.builder().baseUri(server.baseUri()).build());
  }

  private record Response(int status, String body) {}

  private record CapturedRequest(
      String method,
      String path,
      String authorization,
      String accept,
      String contentType,
      String body) {}

  @FunctionalInterface
  private interface Responder {
    Response respond(HttpExchange request);
  }

  private static final class TestServer implements AutoCloseable {
    private final HttpServer server;
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private Responder responder = request -> new Response(500, "no response configured");

    private TestServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.start();
    }

    private URI baseUri() {
      return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
    }

    private List<CapturedRequest> requests() {
      return List.copyOf(requests);
    }

    private void respondWith(int status, String body) {
      respondUsing(request -> new Response(status, body));
    }

    private void respondUsing(Responder value) {
      responder = value;
    }

    private void handle(HttpExchange exchange) throws IOException {
      byte[] requestBody = exchange.getRequestBody().readAllBytes();
      requests.add(
          new CapturedRequest(
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              exchange.getRequestHeaders().getFirst("Authorization"),
              exchange.getRequestHeaders().getFirst("Accept"),
              exchange.getRequestHeaders().getFirst("Content-Type"),
              new String(requestBody, StandardCharsets.UTF_8)));
      Response response = responder.respond(exchange);
      byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(response.status(), responseBody.length);
      exchange.getResponseBody().write(responseBody);
      exchange.close();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
