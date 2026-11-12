package com.management.kumitegametests;

import static com.management.testsupport.IntegrationTestBase.addressOf;
import static com.management.testsupport.IntegrationTestBase.request;
import static com.management.testsupport.IntegrationTestBase.send;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.athlete.data.AthleteRepository;
import com.management.filters.RequestCorrelationFilter;
import com.management.testsupport.IntegrationTestBase;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin rules, proven over real HTTP against the application under its real profiles.
 *
 * <p>Every test here depends on {@code Origin} arriving: a preflight is only recognised by it, and
 * a refusal only happens because of it.
 */
class CorsIT {

  private static final String LOCAL_ORIGIN = "http://localhost:5173";
  private static final String OTHER_ORIGIN = "https://other.example";
  private static final String ATHLETES = "/api/v1/athletes";
  private static final String GAMES = "/api/v1/kumite-games";
  private static final String LIVENESS = "/actuator/health/liveness";
  private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
  private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
  private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
  private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
  private static final String CORRELATION_ID = "X-Correlation-ID";

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
        send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "POST", "authorization,content-type"));

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
        send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "GET", CORRELATION_ID));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(header(response, ALLOW_HEADERS)).containsIgnoringCase(CORRELATION_ID);
  }

  @Test
  void preflight_fromOtherOrigin_isRefused() throws Exception {
    HttpResponse<String> response = send(preflight(dev, ATHLETES, OTHER_ORIGIN, "POST"));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  @Test
  void preflight_forMethodTheApiDoesNotUse_isRefused() throws Exception {
    HttpResponse<String> response = send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "PATCH"));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void preflight_forHeaderTheApiDoesNotUse_isRefused() throws Exception {
    HttpResponse<String> response =
        send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "POST", "x-not-used"));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void refusal_carriesItsIdAndIsNotProblemDetail() throws Exception {
    HttpResponse<String> response = send(preflight(dev, ATHLETES, OTHER_ORIGIN, "POST"));

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
      send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "POST"));
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
    HttpResponse<String> response = send(createAthlete(dev, LOCAL_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
    assertThat(header(response, EXPOSE_HEADERS)).contains(CORRELATION_ID, "Location");
    assertThat(response.headers().firstValue(ALLOW_CREDENTIALS)).isEmpty();
  }

  /**
   * A poller on another origin can read the game's tag, and the server's time on every answer: a
   * 304 has no body, so {@code Date} is the only server time it carries.
   */
  @Test
  void publicView_fromAllowedOrigin_exposesItsTagAndTheServerTime_on304Too() throws Exception {
    String game = GAMES + "/" + createGame(dev);
    HttpResponse<String> full = send(get(dev, game, LOCAL_ORIGIN));
    String tag = header(full, "ETag");

    HttpResponse<String> unchanged =
        send(fromOrigin(dev, game, LOCAL_ORIGIN).header("If-None-Match", tag).GET());

    assertThat(full.statusCode()).isEqualTo(200);
    assertThat(unchanged.statusCode()).isEqualTo(304);
    for (HttpResponse<String> answer : List.of(full, unchanged)) {
      assertThat(header(answer, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
      assertThat(header(answer, EXPOSE_HEADERS)).contains("ETag", "Date");
      assertThat(header(answer, "Date")).isNotBlank();
    }
    assertThat(header(unchanged, "ETag")).isEqualTo(tag);
  }

  /** A poller that sends the tag itself asks first, because the header is not a simple one. */
  @Test
  void preflight_withIfNoneMatch_isAllowed() throws Exception {
    HttpResponse<String> response =
        send(preflight(dev, GAMES + "/any", LOCAL_ORIGIN, "GET", "if-none-match"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(header(response, ALLOW_HEADERS)).containsIgnoringCase("if-none-match");
  }

  /** A browser hides the body of an answer with no CORS headers, errors included. */
  @Test
  void failure_fromAllowedOrigin_isReadable() throws Exception {
    HttpResponse<String> response = send(get(dev, ATHLETES + "/none", LOCAL_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
  }

  /** The chain answers this one before a handler is chosen, so MVC mapping would miss it. */
  @Test
  void refusedRequest_fromAllowedOrigin_isReadable() throws Exception {
    HttpResponse<String> response =
        send(fromOrigin(dev, ATHLETES + "/none", LOCAL_ORIGIN).method("POST", noBody()));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
  }

  /** The exception to authentication covers the preflight and nothing after it. */
  @Test
  void realRequest_afterItsPreflight_stillNeedsCredentials() throws Exception {
    HttpResponse<String> preflight =
        send(preflight(dev, ATHLETES, LOCAL_ORIGIN, "POST", "authorization,content-type"));
    HttpResponse<String> request =
        send(
            fromOrigin(dev, ATHLETES, LOCAL_ORIGIN)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}")));

    assertThat(preflight.statusCode()).isEqualTo(200);
    assertThat(request.statusCode()).isEqualTo(401);
    assertThat(header(request, ALLOW_ORIGIN)).isEqualTo(LOCAL_ORIGIN);
  }

  /** A second source would stop Spring Security seeing this one. */
  @Test
  void corsHasOneSourceAndNoBeanNamedForTheFramework() {
    assertThat(dev.getBeansOfType(UrlBasedCorsConfigurationSource.class)).hasSize(1);
    assertThat(dev.containsBean("corsFilter")).isFalse();
  }

  @Test
  void request_fromOtherOrigin_isRefusedBeforeAnythingIsStored() throws Exception {
    AthleteRepository athletes = dev.getBean(AthleteRepository.class);
    long before = athletes.count();

    HttpResponse<String> response = send(createAthlete(dev, OTHER_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
    assertThat(athletes.count()).isEqualTo(before);
  }

  /** Only the listed methods pass, so a {@code HEAD} the mapping would serve is refused too. */
  @Test
  void headRequest_fromAllowedOrigin_isRefused() throws Exception {
    HttpResponse<String> response =
        send(fromOrigin(dev, ATHLETES + "/none", LOCAL_ORIGIN).method("HEAD", noBody()));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void request_withNoOrigin_isNotCrossOrigin() throws Exception {
    HttpResponse<String> response = send(request(dev, ATHLETES + "/none").GET());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  /** The server compares the origin with its own address, so this one is not cross-origin. */
  @Test
  void request_fromItsOwnOrigin_isNotCrossOrigin() throws Exception {
    HttpResponse<String> response = send(get(prod, ATHLETES + "/none", addressOf(prod)));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  @Test
  void prod_refusesTheLocalOrigin() throws Exception {
    HttpResponse<String> preflight = send(preflight(prod, ATHLETES, LOCAL_ORIGIN, "POST"));
    HttpResponse<String> request = send(createAthlete(prod, LOCAL_ORIGIN));

    assertThat(preflight.statusCode()).isEqualTo(403);
    assertThat(request.statusCode()).isEqualTo(403);
  }

  @Test
  void prod_withAnOriginSet_allowsIt() throws Exception {
    try (ConfigurableApplicationContext context =
        boot("prod", Map.of("CORS_ALLOWED_ORIGINS", OTHER_ORIGIN))) {
      HttpResponse<String> response = send(preflight(context, ATHLETES, OTHER_ORIGIN, "POST"));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(header(response, ALLOW_ORIGIN)).isEqualTo(OTHER_ORIGIN);
    }
  }

  /** A preflight carries no credentials, so authentication must never see it first. */
  @Test
  void filter_runsAfterCorrelationAndBeforeSecurity() {
    FilterRegistrationBean<?> filter = dev.getBean("apiCorsFilter", FilterRegistrationBean.class);
    DelegatingFilterProxyRegistrationBean security =
        dev.getBean(DelegatingFilterProxyRegistrationBean.class);

    assertThat(filter.getOrder())
        .isGreaterThan(Ordered.HIGHEST_PRECEDENCE + 1)
        .isLessThan(security.getOrder());
  }

  @Test
  void actuator_ignoresTheOrigin() throws Exception {
    HttpResponse<String> response = send(get(dev, LIVENESS, OTHER_ORIGIN));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(ALLOW_ORIGIN)).isEmpty();
  }

  /**
   * A CORS filter ends any preflight it sees, and refuses one on a path with no configuration, so
   * this proves none sees the actuator: neither the API's, nor the one in the security chain.
   */
  @Test
  void actuator_preflightIsNotAnsweredByCors() throws Exception {
    HttpResponse<String> response = send(preflight(dev, LIVENESS, LOCAL_ORIGIN, "GET"));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).doesNotContain("Invalid CORS request");
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

  private static HttpRequest.Builder createAthlete(
      ConfigurableApplicationContext context, String origin) {
    return fromOrigin(context, ATHLETES, origin)
        .header("Authorization", IntegrationTestBase.adminCredentials())
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString("{\"name\":\"Kenji\"}"));
  }

  /** A game with two new athletes and no panel, created as the Admin, from no origin. */
  private static String createGame(ConfigurableApplicationContext context) throws Exception {
    String body =
        """
        {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
         "akaAthleteId": "%s", "aoAthleteId": "%s", "panel": []}
        """
            .formatted(createAthleteId(context), createAthleteId(context));
    HttpResponse<String> created = send(asAdmin(context, GAMES, body));
    assertThat(created.statusCode()).isEqualTo(201);
    return idFrom(created.headers().firstValue("Location").orElseThrow());
  }

  private static String createAthleteId(ConfigurableApplicationContext context) throws Exception {
    HttpResponse<String> created = send(asAdmin(context, ATHLETES, "{\"name\":\"Kenji\"}"));
    assertThat(created.statusCode()).isEqualTo(201);
    return idFrom(created.headers().firstValue("Location").orElseThrow());
  }

  private static HttpRequest.Builder asAdmin(
      ConfigurableApplicationContext context, String path, String body) {
    return request(context, path)
        .header("Authorization", IntegrationTestBase.adminCredentials())
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(body));
  }

  private static String idFrom(String location) {
    return location.substring(location.lastIndexOf('/') + 1);
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
