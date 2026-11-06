package com.management.kumitegametests;

import static com.management.testsupport.IntegrationTestBase.addressOf;
import static com.management.testsupport.IntegrationTestBase.request;
import static com.management.testsupport.IntegrationTestBase.send;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.filters.RequestCorrelationFilter;
import com.management.repositories.PlayerRepository;
import com.management.testsupport.IntegrationTestBase;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/**
 * Cross-origin rules, proven over real HTTP against the application under its real profiles.
 *
 * <p>Every test here depends on {@code Origin} arriving: a preflight is only recognised by it, and
 * a refusal only happens because of it.
 */
class CorsIT {

  private static final String LOCAL_ORIGIN = "http://localhost:5173";
  private static final String OTHER_ORIGIN = "https://other.example";
  private static final String PLAYERS = "/api/v1/players";
  private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
  private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
  private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
  private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
  private static final String CORRELATION_ID = "X-Correlation-ID";

  /** Where Spring Security's filter chain sits by default, once it is added. */
  private static final int SECURITY_CHAIN_ORDER = -100;

  private static ConfigurableApplicationContext dev;
  private static ConfigurableApplicationContext prod;

  @BeforeAll
  static void startBothProfiles() {
    dev = boot("dev", Map.of());
    prod = boot("prod", Map.of());
  }

  @AfterAll
  static void stop() {
    dev.close();
    prod.close();
  }

  @Test
  void preflight_fromAllowedOrigin_getsTheAllowHeadersWithNoCredentials() throws Exception {
    HttpResponse<String> response =
        send(preflight(dev, PLAYERS, LOCAL_ORIGIN, "POST", "authorization,content-type"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
    assertThat(header(response, ALLOW_METHODS)).isEqualTo("GET,POST,PUT,DELETE");
    assertThat(header(response, ALLOW_HEADERS))
        .containsIgnoringCase("authorization")
        .containsIgnoringCase("content-type");
    assertThat(response.headers().firstValue(ALLOW_CREDENTIALS)).isEmpty();
  }

  @Test
  void preflight_fromAllowedOrigin_allowsTheCorrelationHeader() throws Exception {
    HttpResponse<String> response =
        send(preflight(dev, PLAYERS, LOCAL_ORIGIN, "GET", CORRELATION_ID));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(header(response, ALLOW_HEADERS)).containsIgnoringCase(CORRELATION_ID);
  }

  @Test
  void preflight_fromOtherOrigin_isRefused() throws Exception {
    HttpResponse<String> response = send(preflight(dev, PLAYERS, OTHER_ORIGIN, "POST"));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  @Test
  void preflight_forMethodTheApiDoesNotUse_isRefused() throws Exception {
    HttpResponse<String> response = send(preflight(dev, PLAYERS, LOCAL_ORIGIN, "PATCH"));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void preflight_forHeaderTheApiDoesNotUse_isRefused() throws Exception {
    HttpResponse<String> response =
        send(preflight(dev, PLAYERS, LOCAL_ORIGIN, "POST", "x-not-used"));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void refusal_carriesItsIdAndIsNotProblemDetail() throws Exception {
    HttpResponse<String> response = send(preflight(dev, PLAYERS, OTHER_ORIGIN, "POST"));

    assertThat(response.headers().firstValue(CORRELATION_ID)).isPresent();
    assertThat(response.headers().firstValue("Content-Type")).isEmpty();
    assertThat(response.body()).isEqualTo("Invalid CORS request");
  }

  /** The filter answers before a handler is chosen, so the line has no route. */
  @Test
  void preflight_logsItsOneLineWithNoRoute() throws Exception {
    Logger log = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    log.addAppender(lines);

    try {
      send(preflight(dev, PLAYERS, LOCAL_ORIGIN, "POST"));
    } finally {
      log.detachAppender(lines);
    }

    assertThat(lines.list)
        .singleElement()
        .satisfies(line ->
            assertThat(line.getKeyValuePairs().toString())
                .contains("method=\"OPTIONS\"", "route=\"UNMATCHED\"", "status=\"200\""));
  }

  @Test
  void request_fromAllowedOrigin_exposesTheIdAndLocation() throws Exception {
    HttpResponse<String> response = send(createPlayer(dev, LOCAL_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
    assertThat(header(response, EXPOSE_HEADERS)).contains(CORRELATION_ID, "Location");
    assertThat(response.headers().firstValue(ALLOW_CREDENTIALS)).isEmpty();
  }

  /** A browser hides the body of an answer with no CORS headers, errors included. */
  @Test
  void failure_fromAllowedOrigin_isReadable() throws Exception {
    HttpResponse<String> response = send(get(dev, PLAYERS + "/none", LOCAL_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
  }

  /** The dispatcher answers this one before a handler is chosen, so MVC mapping would miss it. */
  @Test
  void refusedMethod_fromAllowedOrigin_isReadable() throws Exception {
    HttpResponse<String> response =
        send(fromOrigin(dev, PLAYERS + "/none", LOCAL_ORIGIN).method("POST", noBody()));

    assertThat(response.statusCode()).isEqualTo(405);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
  }

  @Test
  void request_fromOtherOrigin_isRefusedBeforeAnythingIsStored() throws Exception {
    PlayerRepository players = dev.getBean(PlayerRepository.class);
    long before = players.count();

    HttpResponse<String> response = send(createPlayer(dev, OTHER_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
    assertThat(players.count()).isEqualTo(before);
  }

  /** Only the listed methods pass, so a {@code HEAD} the mapping would serve is refused too. */
  @Test
  void headRequest_fromAllowedOrigin_isRefused() throws Exception {
    HttpResponse<String> response =
        send(fromOrigin(dev, PLAYERS + "/none", LOCAL_ORIGIN).method("HEAD", noBody()));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void request_withNoOrigin_isNotCrossOrigin() throws Exception {
    HttpResponse<String> response = send(request(dev, PLAYERS + "/none").GET());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  /** The server compares the origin with its own address, so this one is not cross-origin. */
  @Test
  void request_fromItsOwnOrigin_isNotCrossOrigin() throws Exception {
    HttpResponse<String> response = send(get(prod, PLAYERS + "/none", addressOf(prod)));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  @Test
  void prod_refusesTheLocalOrigin() throws Exception {
    HttpResponse<String> preflight = send(preflight(prod, PLAYERS, LOCAL_ORIGIN, "POST"));
    HttpResponse<String> request = send(createPlayer(prod, LOCAL_ORIGIN));

    assertThat(preflight.statusCode()).isEqualTo(403);
    assertThat(request.statusCode()).isEqualTo(403);
  }

  @Test
  void prod_withAnOriginSet_allowsIt() throws Exception {
    try (ConfigurableApplicationContext context =
        boot("prod", Map.of("CORS_ALLOWED_ORIGINS", OTHER_ORIGIN))) {
      HttpResponse<String> response = send(preflight(context, PLAYERS, OTHER_ORIGIN, "POST"));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(OTHER_ORIGIN);
    }
  }

  /** A preflight carries no credentials, so authentication must never see it first. */
  @Test
  void filter_runsAfterCorrelationAndBeforeSecurity() {
    FilterRegistrationBean<?> filter = dev.getBean("apiCorsFilter", FilterRegistrationBean.class);

    assertThat(filter.getOrder())
        .isGreaterThan(Ordered.HIGHEST_PRECEDENCE + 1)
        .isLessThan(SECURITY_CHAIN_ORDER);
  }

  @Test
  void actuator_ignoresTheOrigin() throws Exception {
    HttpResponse<String> response = send(get(dev, "/actuator/health", OTHER_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  /** The filter ends any preflight it sees, so this proves it never sees the actuator. */
  @Test
  void actuator_preflightIsNotAnsweredByCors() throws Exception {
    HttpResponse<String> response = send(preflight(dev, "/actuator/health", LOCAL_ORIGIN, "GET"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Allow")).isPresent();
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "*",
        "https://*.example",
        "https://scores.example/",
        "scores.example",
        "https://scores.example:443",
        "http://scores.example:80"
      })
  void startup_withAnythingButAnExactOrigin_failsNamingTheKey(String origin) {
    assertThatThrownBy(() -> boot("dev", Map.of("cors.allowed-origins", origin)))
        .hasStackTraceContaining("cors")
        .hasStackTraceContaining("must be one exact origin");
  }

  private static ConfigurableApplicationContext boot(String profile, Map<String, String> args) {
    return IntegrationTestBase.startApplication(profile, IntegrationTestBase.replicaSetUri(), args);
  }

  private static HttpRequest.Builder preflight(
      ConfigurableApplicationContext context, String path, String origin, String method) {
    return fromOrigin(context, path, origin)
        .method("OPTIONS", noBody())
        .header("Access-Control-Request-Method", method);
  }

  private static HttpRequest.Builder preflight(
      ConfigurableApplicationContext context,
      String path,
      String origin,
      String method,
      String headers) {
    return preflight(context, path, origin, method)
        .header("Access-Control-Request-Headers", headers);
  }

  private static HttpRequest.Builder createPlayer(
      ConfigurableApplicationContext context, String origin) {
    return fromOrigin(context, PLAYERS, origin)
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}"));
  }

  private static HttpRequest.Builder get(
      ConfigurableApplicationContext context, String path, String origin) {
    return fromOrigin(context, path, origin).GET();
  }

  private static HttpRequest.Builder fromOrigin(
      ConfigurableApplicationContext context, String path, String origin) {
    return request(context, path).header("Origin", origin);
  }

  private static BodyPublisher noBody() {
    return BodyPublishers.noBody();
  }

  private static String header(HttpResponse<String> response, String name) {
    return response.headers().firstValue(name).orElseThrow();
  }
}
