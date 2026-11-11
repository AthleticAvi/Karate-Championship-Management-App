package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.athlete.data.AthleteDocumentMapper;
import com.management.athlete.data.AthleteRepository;
import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.referee.data.RefereeDocumentMapper;
import com.management.referee.data.RefereeRepository;
import com.management.testsupport.IntegrationTestBase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
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

  private ResponseEntity<String> read(String gameId) {
    return asScorekeeper().getForEntity(GAMES + "/" + gameId, String.class);
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
  void create_storesOneDocument_atSchemaVersionOne_withVersionAndInstants() {
    String gameId = create();

    Document stored = stored(gameId);
    assertThat(stored.get("schemaVersion")).isEqualTo(1);
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
                     "medicalTimeMs": 180000, "timeShownToleranceMs": 1000}
                    """),
                String.class);

    assertThat(changed.getStatusCode().value()).isEqualTo(409);
    assertThat(body(read(gameId)).get("settings").get("durationMs").asLong()).isEqualTo(120_000);
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
}
