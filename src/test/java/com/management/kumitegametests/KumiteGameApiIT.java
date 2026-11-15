package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Games on a real replica set, over HTTP: one write per action, retries, races, references. */
class KumiteGameApiIT extends IntegrationTestBase {

  private static final String GAMES = "/api/v1/kumite-games";
  private static final String COLLECTION = "kumiteGame";

  @Autowired private AthleteRepository athletes;
  @Autowired private RefereeRepository referees;
  @Autowired private KumiteGameRepository games;
  @Autowired private TestRestTemplate anonymous;

  private String aka;
  private String ao;
  private String referee;

  @BeforeEach
  void athletesAndReferee() {
    aka = athletes.save(AthleteDocumentMapper.toNewDocument("Kenji", 61.5)).getId();
    ao = athletes.save(AthleteDocumentMapper.toNewDocument("Haruto", null)).getId();
    referee = referees.save(RefereeDocumentMapper.toNewDocument("Sato")).getId();
  }

  private static HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static JsonNode body(ResponseEntity<String> response) {
    return JsonMapper.shared().readTree(response.getBody());
  }

  private String create() {
    ResponseEntity<String> created =
        asScorekeeper()
            .postForEntity(
                GAMES,
                json(
                    """
                    {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                     "akaAthleteId": "%s", "aoAthleteId": "%s",
                     "panel": [{"refereeId": "%s", "role": "REFEREE"}]}
                    """
                        .formatted(aka, ao, referee)),
                String.class);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return body(created).get("game").get("id").asString();
  }

  private ResponseEntity<String> post(
      TestRestTemplate as, String gameId, String resource, String body) {
    return as.postForEntity(GAMES + "/" + gameId + "/" + resource, json(body), String.class);
  }

  private static String score(String id, String side) {
    return """
    {"id": "%s", "side": "%s", "level": "YUKO", "source": "JUDGES"}
    """
        .formatted(id, side);
  }

  /** The full view, which every check on what was recorded reads. */
  private ResponseEntity<String> read(String gameId) {
    return asScorekeeper().getForEntity(GAMES + "/" + gameId + "/full-view", String.class);
  }

  /** The public view, read with no credentials, sending a tag when one is given. */
  private ResponseEntity<String> readPublic(String gameId, @Nullable String tag) {
    HttpHeaders headers = new HttpHeaders();
    if (tag != null) {
      headers.setIfNoneMatch(tag);
    }
    return anonymous.exchange(
        GAMES + "/" + gameId, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private static String tagOf(ResponseEntity<String> answer) {
    String tag = answer.getHeaders().getETag();
    assertThat(tag).as("the answer carries a tag").isNotNull();
    return Objects.requireNonNull(tag);
  }

  private void rename(String resource, String id, String body) {
    ResponseEntity<String> renamed =
        asAdmin()
            .exchange("/api/v1/" + resource + "/" + id, HttpMethod.PUT, json(body), String.class);
    assertThat(renamed.getStatusCode().value()).isEqualTo(200);
  }

  private Document stored(String gameId) {
    return mongoTemplate.getCollection(COLLECTION).find(new Document("_id", gameId)).first();
  }

  /**
   * The database is empty at the start of a test even though another test just filled it: each test
   * writes what it needs, never inherits it. See {@code
   * context/workflow/web-frameworks/spring-boot/testing/integration.md}: clean before, not after.
   */
  @Test
  void eachTestStartsWithNoGame() {
    assertThat(games.count()).as("whatever ran before this test, its data is gone").isZero();
  }

  @Test
  void create_storesOneDocument_atTheCurrentSchemaVersion_withVersionAndInstants() {
    String gameId = create();

    Document stored = stored(gameId);
    assertThat(stored.get("schemaVersion")).isEqualTo(6);
    assertThat(stored.get("version")).isEqualTo(1L);
    assertThat(stored.get("createdAt")).isInstanceOf(Date.class);
    assertThat(stored.get("modifiedAt")).isInstanceOf(Date.class);
    assertThat(mongoTemplate.getCollection(COLLECTION).countDocuments()).isEqualTo(1);

    JsonNode game = body(read(gameId));
    assertThat(game.get("status").asString()).isEqualTo("NOT_STARTED");
    assertThat(game.get("aka").get("athlete").get("name").asString()).isEqualTo("Kenji");
    assertThat(game.get("panel").get(0).get("referee").get("name").asString()).isEqualTo("Sato");
  }

  /** A score lands on the game alone: the athlete's document is not written. */
  @Test
  void score_isOneWriteToOneDocument() {
    String gameId = create();
    long athleteVersion = athletes.findById(aka).orElseThrow().getVersion();
    long gameVersion = games.findById(gameId).orElseThrow().getVersion();

    ResponseEntity<String> scored = post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));

    assertThat(scored.getStatusCode().value()).isEqualTo(200);
    assertThat(body(scored).get("game").get("aka").get("total").asInt()).isEqualTo(1);
    assertThat(athletes.findById(aka).orElseThrow().getVersion()).isEqualTo(athleteVersion);
    assertThat(games.findById(gameId).orElseThrow().getVersion()).isEqualTo(gameVersion + 1);
  }

  /** Released together by a latch, so the appends genuinely race on the version. */
  @Test
  void concurrentAppends_allLand() throws Exception {
    String gameId = create();
    int writers = 8;
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(writers);
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (int i = 0; i < writers; i++) {
        String body = score("race-" + i, i % 2 == 0 ? "AKA" : "AO");
        answers.add(
            pool.submit(() -> {
              go.await();
              return post(asScorekeeper(), gameId, "scores", body).getStatusCode().value();
            }));
      }
      go.countDown();
      for (Future<Integer> answer : answers) {
        assertThat(answer.get(30, TimeUnit.SECONDS)).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }

    JsonNode game = body(read(gameId));
    assertThat(game.get("aka").get("scores")).hasSize(writers / 2);
    assertThat(game.get("ao").get("scores")).hasSize(writers / 2);
    assertThat(game.get("aka").get("total").asInt() + game.get("ao").get("total").asInt())
        .isEqualTo(writers);
  }

  @Test
  void sameIdAndPayload_appliesOnce_andAnswersTheStoredResult() {
    String gameId = create();
    ResponseEntity<String> first = post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    long version = games.findById(gameId).orElseThrow().getVersion();

    ResponseEntity<String> retry = post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));

    assertThat(retry.getStatusCode().value()).isEqualTo(200);
    assertThat(body(retry).get("raisedWarnings")).isEqualTo(body(first).get("raisedWarnings"));
    assertThat(body(retry).get("game").get("aka").get("scores")).hasSize(1);
    assertThat(games.findById(gameId).orElseThrow().getVersion()).isEqualTo(version);
  }

  @Test
  void sameIdWithAnotherPayloadOrAccount_isRefused() {
    String gameId = create();
    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));

    ResponseEntity<String> otherPayload =
        post(asScorekeeper(), gameId, "scores", score("s1", "AO"));
    ResponseEntity<String> otherAccount = post(asAdmin(), gameId, "scores", score("s1", "AKA"));

    assertThat(otherPayload.getStatusCode().value()).isEqualTo(409);
    assertThat(otherAccount.getStatusCode().value()).isEqualTo(409);
    assertThat(body(read(gameId)).get("aka").get("scores")).hasSize(1);
  }

  @Test
  void settingsAfterTheStart_areRefused() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "clock-actions",
        """
        {"id": "c1", "timer": "MAIN", "action": "START", "timeShownMs": 120000}
        """);

    ResponseEntity<String> changed =
        asScorekeeper()
            .exchange(
                GAMES + "/" + gameId + "/settings",
                HttpMethod.PUT,
                json(
                    """
                    {"durationMs": 90000, "durationsOfferedMs": [90000],
                     "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1}, "leadToWin": 8,
                     "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
                     "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000],
                     "medicalTimeMs": 180000, "timeShownToleranceMs": 1000,
                     "expectedVersion": %d}
                    """
                        .formatted(version(gameId))),
                String.class);

    assertThat(changed.getStatusCode().value()).isEqualTo(409);
    assertThat(body(read(gameId)).get("settings").get("durationMs").asLong()).isEqualTo(120_000);
  }

  /**
   * The panel limit is not a field of a settings change, so one that sends it is refused, naming
   * it, and changes nothing. It used to be ignored with a 200, and the limit stayed the same.
   */
  @Test
  void settingsChange_sendingThePanelLimit_isRefusedNamingIt_andChangesNothing() {
    String gameId = create();
    long before = version(gameId);

    ResponseEntity<String> changed =
        asScorekeeper()
            .exchange(
                GAMES + "/" + gameId + "/settings",
                HttpMethod.PUT,
                json(
                    """
                    {"durationMs": 90000, "durationsOfferedMs": [90000],
                     "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1}, "leadToWin": 8,
                     "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
                     "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000],
                     "medicalTimeMs": 180000, "timeShownToleranceMs": 1000,
                     "panelLimit": 3, "expectedVersion": %d}
                    """
                        .formatted(before)),
                String.class);

    assertThat(changed.getStatusCode().value()).isEqualTo(400);
    assertThat(changed.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(body(changed).path("errors").propertyNames()).containsExactly("panelLimit");
    JsonNode game = body(read(gameId));
    assertThat(game.get("version").asLong()).isEqualTo(before);
    assertThat(game.get("settings").get("durationMs").asLong()).isEqualTo(120_000);
    assertThat(game.get("settings").get("panelLimit").asInt()).isEqualTo(10);
  }

  @Test
  void deletingNamedAthleteOrReferee_isRefused() {
    create();
    String spare = athletes.save(AthleteDocumentMapper.toNewDocument("Spare", null)).getId();

    ResponseEntity<String> athlete =
        asAdmin().exchange("/api/v1/athletes/" + aka, HttpMethod.DELETE, null, String.class);
    ResponseEntity<String> official =
        asAdmin().exchange("/api/v1/referees/" + referee, HttpMethod.DELETE, null, String.class);
    ResponseEntity<String> unused =
        asAdmin().exchange("/api/v1/athletes/" + spare, HttpMethod.DELETE, null, String.class);

    assertThat(athlete.getStatusCode().value()).isEqualTo(409);
    assertThat(official.getStatusCode().value()).isEqualTo(409);
    assertThat(unused.getStatusCode().value()).isEqualTo(204);
    assertThat(athletes.existsById(aka)).isTrue();
    assertThat(referees.existsById(referee)).isTrue();
  }

  /** The accepted race: an athlete deleted after the game named it reads as unknown, not a 500. */
  @Test
  void missingAthleteOrReferee_readsAsUnknown() {
    String gameId = create();
    athletes.deleteById(ao);
    referees.deleteById(referee);

    ResponseEntity<String> read = read(gameId);

    assertThat(read.getStatusCode().value()).isEqualTo(200);
    JsonNode game = body(read);
    assertThat(game.get("ao").get("athlete").get("known").asBoolean()).isFalse();
    assertThat(game.get("ao").get("athlete").get("name").isNull()).isTrue();
    assertThat(game.get("panel").get(0).get("referee").get("known").asBoolean()).isFalse();
  }

  @Test
  void gameWithAnAthleteThatDoesNotExist_isRefused_andNothingIsStored() {
    ResponseEntity<String> created =
        asScorekeeper()
            .postForEntity(
                GAMES,
                json(
                    """
                    {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                     "akaAthleteId": "%s", "aoAthleteId": "nobody"}
                    """
                        .formatted(aka)),
                String.class);

    assertThat(created.getStatusCode().value()).isEqualTo(400);
    assertThat(games.count()).isZero();
  }

  /** SENSHU is stored as its records, and the holder and the closure are derived on each read. */
  @Test
  void senshu_isRecordedAsGiven_derivedOnRead_andWarnsOnTheEntry() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "clock-actions",
        """
        {"id": "c1", "timer": "MAIN", "action": "START", "timeShownMs": 120000}
        """);
    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    ResponseEntity<String> awarded =
        post(
            asScorekeeper(),
            gameId,
            "senshu-records",
            """
            {"id": "k1", "side": "AKA", "kind": "AWARDED"}
            """);

    ResponseEntity<String> opposed = post(asScorekeeper(), gameId, "scores", score("s2", "AO"));
    ResponseEntity<String> again =
        post(
            asScorekeeper(),
            gameId,
            "senshu-records",
            """
            {"id": "k2", "side": "AO", "kind": "AWARDED"}
            """);

    assertThat(awarded.getStatusCode().value()).isEqualTo(200);
    assertThat(body(awarded).get("game").get("aka").get("senshu").asBoolean()).isTrue();
    assertThat(body(opposed).get("raisedWarnings").get(0).asString())
        .isEqualTo("SENSHU_WHEN_BOTH_SCORED");
    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(stored(gameId).getList("senshuRecords", Document.class)).hasSize(1);

    post(
        asScorekeeper(),
        gameId,
        "amendments",
        """
        {"id": "m1", "kind": "CANCEL", "targetId": "k1", "reason": "video review"}
        """);

    JsonNode game = body(read(gameId));
    assertThat(game.get("aka").get("senshu").asBoolean()).isFalse();
    assertThat(game.get("ao").get("senshu").asBoolean()).isFalse();
    assertThat(game.get("senshuClosed").asBoolean()).isFalse();
    assertThat(game.get("senshuRecords").get(0).get("cancelled").asBoolean()).isTrue();
    assertThat(
            game.get("ao").get("scores").get(0).get("meta").get("raisedWarnings").get(0).asString())
        .isEqualTo("SENSHU_WHEN_BOTH_SCORED");
  }

  /**
   * The suggestion through the real beans: live on each read, kept on the result it was recorded
   * against, and a different result applied with a warning.
   */
  @Test
  void result_keepsTheSuggestion_andOneThatDiffers_isAppliedWithWarning() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "clock-actions",
        """
        {"id": "c1", "timer": "MAIN", "action": "START", "timeShownMs": 120000}
        """);
    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    post(
        asScorekeeper(),
        gameId,
        "clock-actions",
        """
        {"id": "c2", "timer": "MAIN", "action": "TIME_UP", "timeShownMs": 0}
        """);
    JsonNode suggested = body(read(gameId)).get("suggestedResult");
    assertThat(suggested.get("winner").asString()).isEqualTo("AKA");
    assertThat(suggested.get("reason").asString()).isEqualTo("POINTS");

    ResponseEntity<String> recorded =
        post(
            asScorekeeper(),
            gameId,
            "results",
            """
            {"id": "r1", "outcome": "WIN", "winner": "AO", "reason": "HANTEI",
             "bothDisqualified": false, "expectedVersion": %d}
            """
                .formatted(version(gameId)));

    assertThat(status(recorded)).isEqualTo(200);
    assertThat(body(recorded).get("raisedWarnings").get(0).asString())
        .isEqualTo("RESULT_DIFFERS_FROM_SUGGESTION");
    Document result = stored(gameId).getList("results", Document.class).getFirst();
    assertThat(result.get("suggested", Document.class).get("winner")).isEqualTo("AKA");
    assertThat(stored(gameId)).doesNotContainKey("suggestedResult");
  }

  /** A status change chosen against the game as it now stands. */
  private String statusChange(String gameId, String id, String kind, @Nullable String reason) {
    long version = version(gameId);
    return reason == null
        ? "{\"id\": \"%s\", \"kind\": \"%s\", \"expectedVersion\": %d}".formatted(id, kind, version)
        : "{\"id\": \"%s\", \"kind\": \"%s\", \"reason\": \"%s\", \"expectedVersion\": %d}"
            .formatted(id, kind, reason, version);
  }

  /** The version the full view shows now: what a screen that just read the game would send. */
  private long version(String gameId) {
    return body(read(gameId)).get("version").asLong();
  }

  private static int status(ResponseEntity<String> response) {
    return response.getStatusCode().value();
  }

  /** A KIKEN result chosen against {@code version}. */
  private static String kiken(String id, long version) {
    return """
    {"id": "%s", "outcome": "WIN", "winner": "AKA", "reason": "KIKEN", "bothDisqualified": false,
     "expectedVersion": %d}
    """
        .formatted(id, version);
  }

  /** The ids a list answers, in its order. Read with no credentials: anyone may list games. */
  private List<String> listed(String query) {
    ResponseEntity<String> answer = anonymous.getForEntity(GAMES + query, String.class);
    assertThat(status(answer)).isEqualTo(200);
    return body(answer)
        .get("content")
        .valueStream()
        .map(game -> game.get("id").asString())
        .toList();
  }

  /**
   * One game per status a list tells apart, and one whose AO athlete was replaced: the filters read
   * the status and the sides the entries fold to, which nothing stores.
   */
  @Test
  void list_filtersByStatusAndByTheAthleteOnEitherSideNow_newestFirst() {
    String replaced = create();
    String ren = athletes.save(AthleteDocumentMapper.toNewDocument("Ren", null)).getId();
    post(
        asScorekeeper(),
        replaced,
        "assignments",
        """
        {"id": "a1", "kind": "ATHLETE", "side": "AO", "athleteId": "%s"}
        """
            .formatted(ren));
    String fighting = create();
    post(
        asScorekeeper(),
        fighting,
        "clock-actions",
        """
        {"id": "c1", "timer": "MAIN", "action": "START", "timeShownMs": 120000}
        """);
    String decided = create();
    post(asScorekeeper(), decided, "results", kiken("r1", version(decided)));
    List<String> newestFirst =
        games.findAll().stream()
            .sorted(
                Comparator.comparing(KumiteGameDocument::getCreatedAt)
                    .reversed()
                    .thenComparing(KumiteGameDocument::getId))
            .map(KumiteGameDocument::getId)
            .toList();

    assertThat(listed("")).containsExactlyElementsOf(newestFirst);
    assertThat(listed("?sort=createdAt,asc"))
        .as("the order is the server's, whatever the caller asks")
        .containsExactlyElementsOf(newestFirst);
    assertThat(listed("?status=FIGHTING")).containsExactly(fighting);
    assertThat(listed("?status=NOT_STARTED&status=DECIDED"))
        .containsExactlyInAnyOrder(replaced, decided);
    assertThat(listed("?athleteId=" + ao))
        .as("a game whose AO athlete was replaced no longer lists under the one replaced")
        .containsExactlyInAnyOrder(fighting, decided);
    assertThat(listed("?athleteId=" + ren)).containsExactly(replaced);
    assertThat(listed("?athleteId=" + aka + "&status=FIGHTING")).containsExactly(fighting);
    assertThat(listed("?athleteId=nobody")).isEmpty();
  }

  @Test
  void list_isPaged_andSaysWhereThePageSits() {
    for (int game = 0; game < 3; game++) {
      create();
    }

    JsonNode first = body(anonymous.getForEntity(GAMES + "?size=2", String.class));
    JsonNode last = body(anonymous.getForEntity(GAMES + "?page=1&size=2", String.class));

    assertThat(first.get("content").size()).isEqualTo(2);
    assertThat(last.get("content").size()).isEqualTo(1);
    assertThat(last.get("page").toString())
        .isEqualTo("{\"size\":2,\"number\":1,\"totalElements\":3,\"totalPages\":2}");
    assertThat(listed("?page=5&size=2")).isEmpty();
    assertThat(listed("?page=2147483647&size=100")).isEmpty();
    assertThat(listed("?athleteId=")).as("a blank athlete is no filter").hasSize(3);
  }

  /** A summary names who fights and how it ended, and nothing of who recorded it or why. */
  @Test
  void list_answersSummaries_withNoRecorderAndNoFreeText() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "results",
        """
        {"id": "r1", "outcome": "WIN", "winner": "AKA", "reason": "KIKEN",
         "reasonDetail": "AO could not go on", "bothDisqualified": false,
         "expectedVersion": %d}
        """
            .formatted(version(gameId)));

    ResponseEntity<String> answer = anonymous.getForEntity(GAMES, String.class);
    JsonNode summary = body(answer).get("content").get(0);

    assertThat(summary.get("aka").get("athlete").get("name").asString()).isEqualTo("Kenji");
    assertThat(summary.get("ao").get("athlete").get("name").asString()).isEqualTo("Haruto");
    assertThat(summary.get("status").asString()).isEqualTo("DECIDED");
    assertThat(summary.get("currentResult").get("reason").asString()).isEqualTo("KIKEN");
    assertThat(answer.getBody())
        .doesNotContain("AO could not go on", SCOREKEEPER, "recordedBy", "warnings");
  }

  private ResponseEntity<String> createWithPanel(List<String> refereeIds) {
    String panel =
        refereeIds.stream()
            .map(id -> "{\"refereeId\": \"%s\", \"role\": \"JUDGE\"}".formatted(id))
            .collect(Collectors.joining(", "));
    return asScorekeeper()
        .postForEntity(
            GAMES,
            json(
                """
                {"format": "ELIMINATION", "medalGame": false, "extraGame": false,
                 "akaAthleteId": "%s", "aoAthleteId": "%s", "panel": [%s]}
                """
                    .formatted(aka, ao, panel)),
            String.class);
  }

  private static String panelChange(String id, String kind, String refereeId) {
    return """
    {"id": "%s", "kind": "%s", "refereeId": "%s", "role": %s}
    """
        .formatted(id, kind, refereeId, kind.equals("PANEL_ADD") ? "\"JUDGE\"" : "null");
  }

  /**
   * The panel holds at most the configured 10, at creation and when a referee is added, and a
   * referee sits on it once. A panel past the 20 the request once capped reaches the game.
   */
  @Test
  void panel_holdsAtMostTheConfiguredLimit_andEachRefereeOnce() {
    List<String> judges =
        IntStream.rangeClosed(1, 21)
            .mapToObj(judge ->
                referees.save(RefereeDocumentMapper.toNewDocument("Judge " + judge)).getId())
            .toList();

    for (int size : List.of(21, 11)) {
      ResponseEntity<String> tooMany = createWithPanel(judges.subList(0, size));
      assertThat(status(tooMany)).isEqualTo(400);
      assertThat(body(tooMany).get("detail").asString())
          .isEqualTo("A panel holds at most 10 referees, but " + size + " were given.");
      assertThat(body(tooMany).has("errors")).isFalse();
    }
    ResponseEntity<String> full = createWithPanel(judges.subList(0, 10));
    assertThat(status(full)).isEqualTo(201);
    String gameId = body(full).get("game").get("id").asString();

    assertThat(
            status(
                post(
                    asScorekeeper(),
                    gameId,
                    "assignments",
                    panelChange("a1", "PANEL_ADD", judges.get(10)))))
        .isEqualTo(409);
    assertThat(
            status(
                post(
                    asScorekeeper(),
                    gameId,
                    "assignments",
                    panelChange("a2", "PANEL_REMOVE", judges.get(0)))))
        .isEqualTo(200);
    assertThat(
            status(
                post(
                    asScorekeeper(),
                    gameId,
                    "assignments",
                    panelChange("a3", "PANEL_ADD", judges.get(1)))))
        .isEqualTo(409);
    assertThat(body(read(gameId)).get("panel")).hasSize(9);
  }

  /** Such as one stored by a newer build before a rollback: reading it fails, listing does not. */
  @Test
  void list_leavesOutGameThisBuildCannotRead() {
    String readable = create();
    Document newer =
        new Document(stored(readable))
            .append("_id", "from-a-newer-build")
            .append("schemaVersion", KumiteGameDocument.CURRENT_SCHEMA_VERSION + 1);
    mongoTemplate.getCollection(COLLECTION).insertOne(newer);

    assertThat(listed("")).containsExactly(readable);
    assertThat(status(read("from-a-newer-build"))).isEqualTo(500);
  }

  @Test
  void list_withStatusThatDoesNotExist_isBadRequest() {
    assertThat(status(anonymous.getForEntity(GAMES + "?status=PAUSED", String.class)))
        .isEqualTo(400);
  }

  /** The status table end to end: who may take each move, and what each answers. */
  @Test
  void approveReopenAndVoid_followTheStatusTable_andTheRoles() {
    String gameId = create();
    post(asScorekeeper(), gameId, "results", kiken("r1", version(gameId)));

    ResponseEntity<String> reopened =
        post(asScorekeeper(), gameId, "status-changes", statusChange(gameId, "z1", "REOPEN", null));
    assertThat(status(reopened)).isEqualTo(200);
    JsonNode game = body(reopened).get("game");
    assertThat(game.get("status").asString()).isEqualTo("NOT_STARTED");
    assertThat(game.get("currentResult").isNull()).isTrue();
    assertThat(game.get("results")).hasSize(1);

    post(asScorekeeper(), gameId, "results", kiken("r2", version(gameId)));
    ResponseEntity<String> approved =
        post(
            asScorekeeper(), gameId, "status-changes", statusChange(gameId, "z2", "APPROVE", null));
    assertThat(body(approved).get("game").get("status").asString()).isEqualTo("APPROVED");
    assertThat(status(post(asScorekeeper(), gameId, "scores", score("s1", "AKA"))))
        .as("nothing is recorded on an APPROVED game")
        .isEqualTo(409);

    assertThat(
            status(
                post(
                    asScorekeeper(),
                    gameId,
                    "status-changes",
                    statusChange(gameId, "z3", "REOPEN", "protest"))))
        .as("a Scorekeeper cannot reopen an APPROVED game")
        .isEqualTo(403);
    assertThat(
            status(
                post(
                    asAdmin(),
                    gameId,
                    "status-changes",
                    statusChange(gameId, "z4", "REOPEN", null))))
        .as("an Admin's reopen needs a reason")
        .isEqualTo(400);
    ResponseEntity<String> adminReopened =
        post(asAdmin(), gameId, "status-changes", statusChange(gameId, "z5", "REOPEN", "SHIKKAKU"));
    assertThat(body(adminReopened).get("game").get("status").asString()).isEqualTo("DECIDED");

    assertThat(
            status(
                post(
                    asScorekeeper(),
                    gameId,
                    "status-changes",
                    statusChange(gameId, "z6", "VOID", "x"))))
        .as("a Scorekeeper cannot void")
        .isEqualTo(403);
    assertThat(
            status(
                post(
                    asAdmin(), gameId, "status-changes", statusChange(gameId, "z7", "VOID", null))))
        .as("a void needs a reason")
        .isEqualTo(400);
    ResponseEntity<String> voided =
        post(
            asAdmin(),
            gameId,
            "status-changes",
            statusChange(gameId, "z8", "VOID", "protest accepted"));
    assertThat(status(voided)).isEqualTo(200);
    assertThat(body(voided).get("game").get("status").asString()).isEqualTo("VOIDED");
    assertThat(
            status(
                post(
                    asAdmin(),
                    gameId,
                    "status-changes",
                    statusChange(gameId, "z9", "VOID", "again"))))
        .isEqualTo(409);

    Document stored = stored(gameId);
    assertThat(stored.getList("statusChanges", Document.class))
        .extracting(change -> change.getString("kind"))
        .containsExactly("REOPEN", "APPROVE", "REOPEN", "VOID");
    assertThat(stored.getList("results", Document.class)).hasSize(2);
  }

  @Test
  void documentRoundTrip_throughMongo_keepsTheEntries() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "penalties",
        """
        {"id": "p1", "side": "AO", "level": "HANSOKU", "infraction": "JOGAI"}
        """);
    post(
        asScorekeeper(),
        gameId,
        "amendments",
        """
        {"id": "m1", "kind": "CANCEL", "targetId": "p1", "reason": "wrong side"}
        """);

    KumiteGameDocument reloaded = games.findById(gameId).orElseThrow();
    JsonNode game = body(read(gameId));

    assertThat(reloaded.getAmendments()).hasSize(1);
    JsonNode penalty = game.get("ao").get("penalties").get(0);
    assertThat(penalty.get("infraction").asString()).isEqualTo("JOGAI");
    assertThat(penalty.get("cancelled").asBoolean()).isTrue();
    assertThat(penalty.get("meta").get("raisedWarnings").get(0).asString())
        .isEqualTo("HANSOKU_WITHOUT_HANSOKU_CHUI");
  }

  /** Anyone may follow a game, without who recorded what, warnings or notes. */
  @Test
  void publicView_isReadWithNoCredentials_withNoRecorderWarningsOrNotes() {
    String gameId = create();
    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    post(
        asScorekeeper(),
        gameId,
        "results",
        """
        {"id": "r1", "outcome": "WIN", "winner": "AKA", "reason": "KIKEN",
         "reasonDetail": "AO could not go on", "bothDisqualified": false,
         "expectedVersion": %d}
        """
            .formatted(version(gameId)));

    ResponseEntity<String> answer = readPublic(gameId, null);
    ResponseEntity<String> fullView =
        anonymous.getForEntity(GAMES + "/" + gameId + "/full-view", String.class);

    assertThat(answer.getStatusCode().value()).isEqualTo(200);
    JsonNode game = body(answer);
    assertThat(game.get("aka").get("scores").get(0).get("meta").propertyNames())
        .containsExactlyInAnyOrder("id", "order", "recordedAt");
    assertThat(game.has("warnings")).isFalse();
    assertThat(game.has("serverTime")).isTrue();
    assertThat(answer.getBody()).doesNotContain("recordedBy", "raisedWarnings");
    assertThat(game.get("currentResult").get("reason").asString()).isEqualTo("KIKEN");
    assertThat(answer.getBody()).doesNotContain("AO could not go on");
    assertThat(read(gameId).getBody()).contains("AO could not go on");
    assertThat(fullView.getStatusCode().value()).isEqualTo(401);
  }

  /**
   * An unchanged game answers 304 with no body. The tag changes when the game changes, and when an
   * athlete or referee it names is renamed, because the names are read when the game is.
   */
  @Test
  void tag_isNotModifiedUntilTheGameOrAnAthleteOrRefereeItNamesChanges() {
    String gameId = create();
    String created = tagOf(readPublic(gameId, null));

    ResponseEntity<String> unchanged = readPublic(gameId, created);
    assertThat(unchanged.getStatusCode().value()).isEqualTo(304);
    assertThat(unchanged.getBody()).isNull();
    assertThat(tagOf(unchanged)).isEqualTo(created);

    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    ResponseEntity<String> scored = readPublic(gameId, created);
    assertThat(scored.getStatusCode().value()).isEqualTo(200);
    assertThat(body(scored).get("aka").get("total").asInt()).isEqualTo(1);
    String afterScore = tagOf(scored);

    rename("athletes", aka, "{\"name\": \"Kenji Ito\", \"weightKg\": 61.5}");
    ResponseEntity<String> athleteRenamed = readPublic(gameId, afterScore);
    assertThat(athleteRenamed.getStatusCode().value()).isEqualTo(200);
    assertThat(body(athleteRenamed).get("aka").get("athlete").get("name").asString())
        .isEqualTo("Kenji Ito");
    String afterAthlete = tagOf(athleteRenamed);

    rename("referees", referee, "{\"name\": \"Sato Ken\"}");
    ResponseEntity<String> refereeRenamed = readPublic(gameId, afterAthlete);
    assertThat(refereeRenamed.getStatusCode().value()).isEqualTo(200);
    assertThat(body(refereeRenamed).get("panel").get(0).get("referee").get("name").asString())
        .isEqualTo("Sato Ken");

    assertThat(List.of(created, afterScore, afterAthlete, tagOf(refereeRenamed)))
        .doesNotHaveDuplicates();
    assertThat(readPublic(gameId, tagOf(refereeRenamed)).getStatusCode().value()).isEqualTo(304);
  }

  /**
   * A browser keeps the public view and asks with its tag every time, and every 304 still carries
   * the server's time, to the second. The full view is never stored.
   */
  @Test
  void publicView_isKeptAndAskedForAgain_andA304CarriesTheServerTime() {
    String gameId = create();
    String tag = tagOf(readPublic(gameId, null));
    Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    ResponseEntity<String> unchanged = readPublic(gameId, tag);

    Instant after = Instant.now();
    assertThat(unchanged.getStatusCode().value()).isEqualTo(304);
    assertThat(unchanged.getHeaders().getCacheControl()).isEqualTo("no-cache");
    assertThat(Instant.ofEpochMilli(unchanged.getHeaders().getDate())).isBetween(before, after);
    assertThat(readPublic(gameId, null).getHeaders().getCacheControl()).isEqualTo("no-cache");
    assertThat(read(gameId).getHeaders().getCacheControl()).contains("no-store");
  }

  /** The full view is tagged the same way, for the scorekeeper's own polling. */
  @Test
  void fullView_isTagged_andAnswers304WhileUnchanged() {
    String gameId = create();
    String tag = tagOf(read(gameId));
    HttpHeaders headers = new HttpHeaders();
    headers.setIfNoneMatch(tag);

    ResponseEntity<String> unchanged =
        asScorekeeper()
            .exchange(
                GAMES + "/" + gameId + "/full-view",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

    assertThat(unchanged.getStatusCode().value()).isEqualTo(304);
    assertThat(unchanged.getBody()).isNull();
  }

  /**
   * Sends an absolute action chosen against a version another write has since moved past, then the
   * same action against the version now stored. The stale one answers 409 with the game as it now
   * stands and writes nothing; the current one applies.
   *
   * @param shown what the action changes, read from a game
   */
  private void sendStaleThenCurrent(
      TestRestTemplate as,
      HttpMethod method,
      String resource,
      LongFunction<String> body,
      Function<JsonNode, String> shown) {
    String gameId = create();
    long stale = version(gameId);
    post(
        asScorekeeper(),
        gameId,
        "penalties",
        "{\"id\": \"p1\", \"side\": \"AO\", \"level\": \"CHUI\"}");
    long current = version(gameId);
    String before = shown.apply(body(read(gameId)));

    ResponseEntity<String> refused =
        as.exchange(
            GAMES + "/" + gameId + "/" + resource, method, json(body.apply(stale)), String.class);
    ResponseEntity<String> applied =
        as.exchange(
            GAMES + "/" + gameId + "/" + resource, method, json(body.apply(current)), String.class);

    assertThat(current).as("the penalty moved the game on").isGreaterThan(stale);
    assertThat(status(refused)).isEqualTo(409);
    JsonNode answered = body(refused).get("current");
    assertThat(answered.get("version").asLong()).isEqualTo(current);
    assertThat(shown.apply(answered)).as("the stale action wrote nothing").isEqualTo(before);
    assertThat(status(applied)).isEqualTo(200);
    JsonNode game = body(applied).get("game");
    assertThat(game.get("version").asLong()).isEqualTo(current + 1);
    assertThat(shown.apply(game)).isNotEqualTo(before);
  }

  @Test
  void settingsChange_withStaleVersion_isConflict_withCurrentVersion_applies() {
    sendStaleThenCurrent(
        asScorekeeper(),
        HttpMethod.PUT,
        "settings",
        version ->
            """
            {"durationMs": 90000, "durationsOfferedMs": [90000],
             "pointValues": {"IPPON": 3, "WAZA_ARI": 2, "YUKO": 1}, "leadToWin": 8,
             "finalWindowMs": 15000, "lateScoreWindowMs": 1500, "chuiLimit": 3,
             "roundRobinForfeitPoints": 4, "addTimeStepsMs": [10000],
             "medicalTimeMs": 180000, "timeShownToleranceMs": 1000, "expectedVersion": %d}
            """
                .formatted(version),
        game -> game.get("settings").get("durationMs").asString());
  }

  @Test
  void setTime_withStaleVersion_isConflict_withCurrentVersion_applies() {
    sendStaleThenCurrent(
        asScorekeeper(),
        HttpMethod.POST,
        "clock-actions",
        version ->
            """
            {"id": "c1", "timer": "MAIN", "action": "SET_TIME", "timeShownMs": 120000,
             "amountMs": 60000, "expectedVersion": %d}
            """
                .formatted(version),
        game -> game.get("clockActions").toString());
  }

  @Test
  void result_withStaleVersion_isConflict_withCurrentVersion_applies() {
    sendStaleThenCurrent(
        asScorekeeper(),
        HttpMethod.POST,
        "results",
        version -> kiken("r1", version),
        game -> game.get("results").toString());
  }

  @Test
  void statusChange_withStaleVersion_isConflict_withCurrentVersion_applies() {
    sendStaleThenCurrent(
        asAdmin(),
        HttpMethod.POST,
        "status-changes",
        version ->
            "{\"id\": \"v1\", \"kind\": \"VOID\", \"reason\": \"protest\", \"expectedVersion\": %d}"
                .formatted(version),
        game -> game.get("status").asString());
  }

  /**
   * The first attempt landed, so the retry's version is now stale: it still answers as a retry,
   * with the warnings stored with the first and nothing written.
   */
  @Test
  void retriedResult_whoseFirstAttemptLanded_answersAsRetry() {
    String gameId = create();
    long read = version(gameId);
    ResponseEntity<String> first = post(asScorekeeper(), gameId, "results", kiken("r1", read));

    ResponseEntity<String> retry = post(asScorekeeper(), gameId, "results", kiken("r1", read));

    assertThat(status(retry)).isEqualTo(200);
    assertThat(body(retry).get("raisedWarnings")).isEqualTo(body(first).get("raisedWarnings"));
    assertThat(body(retry).get("game").get("version").asLong()).isEqualTo(read + 1);
    assertThat(stored(gameId).getList("results", Document.class)).hasSize(1);
  }

  @Test
  void setTimeWithoutVersion_andStartWithOne_areBadRequests() {
    String gameId = create();
    long current = version(gameId);

    ResponseEntity<String> setTime =
        post(
            asScorekeeper(),
            gameId,
            "clock-actions",
            """
            {"id": "c1", "timer": "MAIN", "action": "SET_TIME", "timeShownMs": 120000,
             "amountMs": 60000}
            """);
    ResponseEntity<String> start =
        post(
            asScorekeeper(),
            gameId,
            "clock-actions",
            """
            {"id": "c2", "timer": "MAIN", "action": "START", "timeShownMs": 120000,
             "expectedVersion": %d}
            """
                .formatted(current));

    assertThat(status(setTime)).isEqualTo(400);
    assertThat(body(setTime).get("detail").asString()).contains("expectedVersion");
    assertThat(status(start)).isEqualTo(400);
    assertThat(stored(gameId).getLong("version")).isEqualTo(current);
  }

  /** Appends carry no version, so a screen that is behind still records them. */
  @Test
  void appends_sentWithNoVersion_landAfterTheGameMovedOn() {
    String gameId = create();
    post(
        asScorekeeper(),
        gameId,
        "penalties",
        "{\"id\": \"p1\", \"side\": \"AO\", \"level\": \"CHUI\"}");

    ResponseEntity<String> started =
        post(
            asScorekeeper(),
            gameId,
            "clock-actions",
            """
            {"id": "c1", "timer": "MAIN", "action": "START", "timeShownMs": 120000}
            """);
    ResponseEntity<String> scored = post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));

    assertThat(status(started)).isEqualTo(200);
    assertThat(status(scored)).isEqualTo(200);
    assertThat(body(read(gameId)).get("aka").get("scores")).hasSize(1);
  }

  @Test
  void bothViews_carryTheStoredVersion() {
    String gameId = create();
    post(asScorekeeper(), gameId, "scores", score("s1", "AKA"));
    long stored = stored(gameId).getLong("version");

    assertThat(body(read(gameId)).get("version").asLong()).isEqualTo(stored);
    assertThat(body(readPublic(gameId, null)).get("version").asLong()).isEqualTo(stored);
  }

  @Test
  void create_answersTheStoredVersion() {
    ResponseEntity<String> created = createWithPanel(List.of(referee));
    assertThat(status(created)).isEqualTo(201);
    JsonNode game = body(created).get("game");

    assertThat(game.get("version").asLong())
        .isEqualTo(stored(game.get("id").asString()).getLong("version"));
  }
}
