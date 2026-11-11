package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.testsupport.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real credentials against real accounts, over real HTTP.
 *
 * <p>On {@link HttpClient}, because it sends {@code Authorization} as given, retries nothing on a
 * 401, and accepts a method the other clients refuse.
 */
class SecurityIT extends IntegrationTestBase {

  private static final String ATHLETES = "/api/v1/athletes";
  private static final String GAMES = "/api/v1/kumite-games";
  private static final String CORRELATION_ID = "X-Correlation-ID";
  private static final String AUTHORIZATION = "Authorization";
  private static final String CHALLENGE = "WWW-Authenticate";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SECRET = "n0t-a-real-p4ssword";
  private static final String NEW_ATHLETE = "{\"name\":\"Kenji\"}";
  @LocalServerPort private int port;

  @Autowired private AthleteRepository athletes;

  /** A game between two athletes stored for it. */
  private String newGame() {
    String aka = athletes.save(AthleteDocumentMapper.toNewDocument("Aka", null)).getId();
    String ao = athletes.save(AthleteDocumentMapper.toNewDocument("Ao", null)).getId();
    return """
    {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
     "akaAthleteId": "%s", "aoAthleteId": "%s"}
    """
        .formatted(aka, ao);
  }

  private HttpRequest.Builder to(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private HttpRequest.Builder post(String path, String body) {
    return to(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body));
  }

  private static void assertRefusal(HttpResponse<String> response, int status, String path) {
    JsonNode body = JsonMapper.shared().readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type")).contains(PROBLEM_JSON);
    assertThat(response.headers().firstValue(CORRELATION_ID)).isPresent();
    assertThat(response.headers().firstValue(CHALLENGE)).isEmpty();
    assertThat(body.get("status").asInt()).isEqualTo(status);
    assertThat(body.get("instance").asString()).isEqualTo(path);
    assertThat(body.get("title").asString()).isNotBlank();
    assertThat(body.get("detail").asString()).isNotBlank();
    assertThat(body.has("type")).isFalse();
  }

  @Test
  void noCredentials_answersProblemDetailWithNoChallenge() throws Exception {
    HttpResponse<String> response = send(post(ATHLETES, NEW_ATHLETE));

    assertRefusal(response, 401, ATHLETES);
    assertThat(athletes.count()).isZero();
  }

  /** The three take different paths through the framework, and must not differ to a caller. */
  @Test
  void everyWrongCredential_answersTheSameRefusal() throws Exception {
    HttpResponse<String> none = send(post(ATHLETES, NEW_ATHLETE));
    HttpResponse<String> wrongPassword =
        send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic(ADMIN, "wrong")));
    HttpResponse<String> unknownAccount =
        send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic("nobody", PASSWORD)));

    assertRefusal(wrongPassword, 401, ATHLETES);
    assertRefusal(unknownAccount, 401, ATHLETES);
    assertThat(wrongPassword.body()).isEqualTo(unknownAccount.body()).isEqualTo(none.body());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Basic not-base64!", "Basic bm9jb2xvbg==", "Basic "})
  void malformedCredentials_areRefused(String header) throws Exception {
    HttpResponse<String> response = send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, header));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue(CHALLENGE)).isEmpty();
  }

  /** The hash has a length limit on encoding, and a longer guess must only be wrong. */
  @Test
  void passwordPastTheHashLimit_isOnlyWrong() throws Exception {
    HttpResponse<String> response =
        send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic(ADMIN, "x".repeat(500))));

    assertRefusal(response, 401, ATHLETES);
  }

  @Test
  void wrongRole_answersProblemDetailAndStoresNothing() throws Exception {
    HttpResponse<String> response =
        send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic(SCOREKEEPER, PASSWORD)));

    assertRefusal(response, 403, ATHLETES);
    assertThat(athletes.count()).isZero();
  }

  @Test
  void rightRole_succeeds() throws Exception {
    String newGame = newGame();
    assertThat(
            send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, adminCredentials()))
                .statusCode())
        .isEqualTo(201);
    assertThat(
            send(post(GAMES, newGame).header(AUTHORIZATION, basic(SCOREKEEPER, PASSWORD)))
                .statusCode())
        .isEqualTo(201);
    assertThat(send(post(GAMES, newGame).header(AUTHORIZATION, adminCredentials())).statusCode())
        .isEqualTo(201);
  }

  @Test
  void publicRead_needsNoCredentials() throws Exception {
    AthleteDocument athlete = athletes.save(AthleteDocumentMapper.toNewDocument("Kenji", null));

    HttpResponse<String> response = send(to(ATHLETES + "/" + athlete.getId()).GET());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Kenji");
  }

  /** Nothing about a miss changes with security on: same status, same body, and its route. */
  @Test
  void publicRead_ofNothing_stillAnswers404() throws Exception {
    HttpResponse<String> response = send(to(ATHLETES + "/none").GET());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Type")).contains(PROBLEM_JSON);
    assertThat(JsonMapper.shared().readTree(response.body()).get("instance").asString())
        .isEqualTo(ATHLETES + "/none");
  }

  @Test
  void validationFailure_stillAnswers400() throws Exception {
    HttpResponse<String> response =
        send(post(ATHLETES, "{}").header(AUTHORIZATION, adminCredentials()));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("errors");
  }

  @Test
  void wrongCredentials_onPublicRead_areStillWrong() throws Exception {
    HttpResponse<String> response =
        send(to(ATHLETES + "/none").header(AUTHORIZATION, basic(ADMIN, "wrong")).GET());

    assertThat(response.statusCode()).isEqualTo(401);
  }

  /** Only the listed methods pass, so these no longer reach the mapping that would serve them. */
  @ParameterizedTest
  @ValueSource(strings = {"HEAD", "OPTIONS"})
  void methodNotInTheTable_isRefusedOnPublicPath(String method) throws Exception {
    HttpResponse<String> response =
        send(to(ATHLETES + "/none").method(method, BodyPublishers.noBody()));

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void unknownPath_isRefusedAndHidesWhatExists() throws Exception {
    assertThat(send(to("/no/such/path").GET()).statusCode()).isEqualTo(401);
    assertThat(
            send(to("/no/such/path").header(AUTHORIZATION, adminCredentials()).GET()).statusCode())
        .isEqualTo(403);
  }

  /** The firewall refuses these through the container, which comes back on the error dispatch. */
  @Test
  void requestTheFirewallRefuses_keepsItsStatusAndId() throws Exception {
    HttpResponse<String> response = send(to(ATHLETES + "/a;b").GET());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue(CORRELATION_ID)).isPresent();
  }

  @Test
  void success_createsNoSession() throws Exception {
    HttpResponse<String> response =
        send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, adminCredentials()));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
  }

  /** With sessions allowed, the framework saves a refused page request in one, to replay it. */
  @Test
  void refusal_createsNoSession() throws Exception {
    HttpResponse<String> response =
        send(to("/actuator/health").header("Accept", "text/html").GET());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
  }

  /** Left on, the framework would answer this with a redirect to a login page that is not here. */
  @Test
  void logoutAndLoginPages_doNotExist() throws Exception {
    assertThat(send(to("/logout").POST(BodyPublishers.noBody())).statusCode()).isEqualTo(401);
    assertThat(send(to("/login").GET()).statusCode()).isEqualTo(401);
  }

  /** What the framework adds to every answer. A client reading these is reading a contract. */
  @Test
  void securityHeaders_areOnEveryAnswer() throws Exception {
    HttpResponse<String> response = send(to(ATHLETES + "/none").GET());

    assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
    assertThat(response.headers().firstValue("Cache-Control")).isPresent();
  }

  @Test
  void credentials_areNeverLogged() throws Exception {
    Logger log = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    log.addAppender(lines);

    try {
      send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic(ADMIN, SECRET)));
      send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, basic(SECRET, SECRET)));
      send(post(ATHLETES, NEW_ATHLETE).header(AUTHORIZATION, adminCredentials()));
    } finally {
      log.detachAppender(lines);
    }

    String logged =
        lines.list.stream()
            .map(line -> line.getFormattedMessage() + line.getKeyValuePairs())
            .collect(Collectors.joining("\n"));
    assertThat(lines.list).isNotEmpty();
    assertThat(logged).doesNotContain(SECRET).doesNotContain(PASSWORD);
  }
}
