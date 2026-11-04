package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.testsupport.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A real request through the real server, read back from every logger in the application.
 *
 * <p>Reads log events and not console text, because a test that boots under {@code prod} changes
 * the shared console format for the rest of the JVM. {@code ProfileStartupIT} covers that output.
 */
class RequestCorrelationIT extends IntegrationTestBase {

  private static final String HEADER = "X-Correlation-ID";
  private static final String SECRET = "client-secret-value";

  @Autowired private TestRestTemplate rest;

  private final Logger log = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final ListAppender<ILoggingEvent> lines =
      new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent line) {
          line.prepareForDeferredProcessing();
          super.append(line);
        }
      };

  @BeforeEach
  void listen() {
    lines.start();
    log.addAppender(lines);
  }

  @AfterEach
  void stopListening() {
    log.detachAppender(lines);
  }

  @Test
  void request_returnsItsIdAndLogsItOnItsOneRequestLine() {
    ResponseEntity<String> response =
        rest.postForEntity("/api/v1/players", player("Kenji"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = idOf(response);
    assertThat(id).isNotBlank();
    assertThat(linesFor(id))
        .singleElement()
        .satisfies(line -> assertThat(line.getMessage()).isEqualTo("Request completed"));
  }

  @Test
  void validIncomingId_isUsed() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HEADER, "from-the-caller");

    ResponseEntity<String> response =
        rest.exchange(
            "/api/v1/players/missing", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getHeaders().getFirst(HEADER)).isEqualTo("from-the-caller");
    assertThat(linesFor("from-the-caller")).hasSize(1);
  }

  @Test
  void invalidIncomingId_isReplacedAndNeverLogged() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HEADER, "not valid {id}");

    ResponseEntity<String> response =
        rest.exchange(
            "/api/v1/players/missing", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    String id = idOf(response);
    assertThat(id).isNotEqualTo("not valid {id}");
    assertThat(linesFor(id)).hasSize(1);
    assertThat(everythingLogged()).doesNotContain("not valid {id}");
  }

  /** A miss is normal traffic: one line, no error level, and the client's value is not repeated. */
  @Test
  void missingResource_logsOneInfoLineThatDoesNotRepeatTheId() {
    ResponseEntity<String> response = rest.getForEntity("/api/v1/players/" + SECRET, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(linesFor(idOf(response)))
        .singleElement()
        .satisfies(line -> assertThat(line.getLevel().toString()).isEqualTo("INFO"));
    assertThat(everythingLogged()).doesNotContain(SECRET);
  }

  @Test
  void unknownPath_stillReturnsAnId() {
    ResponseEntity<String> response = rest.getForEntity("/no/such/path", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(linesFor(idOf(response))).hasSize(1);
  }

  /**
   * The framework's own line for a refused method would repeat the client's word and add a line.
   */
  @Test
  void unknownMethod_logsOneLineThatDoesNotRepeatIt() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(rest.getRootUri() + "/api/v1/players/none"))
            .method("CLIENTWORD", BodyPublishers.noBody())
            .build();

    HttpResponse<Void> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response = client.send(request, BodyHandlers.discarding());
    }

    assertThat(response.statusCode()).isEqualTo(405);
    assertThat(linesFor(response.headers().firstValue(HEADER).orElseThrow())).hasSize(1);
    assertThat(everythingLogged()).doesNotContain("CLIENTWORD");
  }

  @Test
  void credentialsQueryAndBody_areNeverLogged() {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(SECRET);

    rest.postForEntity(
        "/api/v1/players?token=" + SECRET,
        new HttpEntity<>("{\"name\": \"" + SECRET + "\"}", headers),
        String.class);

    assertThat(lines.list).isNotEmpty();
    assertThat(everythingLogged()).doesNotContain(SECRET);
  }

  private static String idOf(ResponseEntity<String> response) {
    return Objects.requireNonNull(response.getHeaders().getFirst(HEADER));
  }

  private static HttpEntity<String> player(String name) {
    return new HttpEntity<>("{\"name\": \"" + name + "\"}", jsonHeaders());
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private List<ILoggingEvent> linesFor(String id) {
    return List.copyOf(lines.list).stream()
        .filter(line -> id.equals(line.getMDCPropertyMap().get("correlationId")))
        .toList();
  }

  private String everythingLogged() {
    StringBuilder all = new StringBuilder();
    List.copyOf(lines.list)
        .forEach(line ->
            all.append(line.getFormattedMessage())
                .append(line.getKeyValuePairs())
                .append(line.getMDCPropertyMap())
                .append('\n'));
    return all.toString();
  }
}
