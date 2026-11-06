package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.dto.KumiteGameResponse;
import com.management.enums.PlayerColor;
import com.management.models.GameWithFighters;
import com.management.models.MatchOutcome;
import com.management.models.RefereeOverride;
import com.management.repositories.KumiteGameRepository;
import com.management.testsupport.IntegrationTestBase;
import com.management.testsupport.KumiteGameBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Who is recorded comes from the signed-in account, all the way to the stored document. */
class OverrideAuditIT extends IntegrationTestBase {

  private static final String ONE_GAME = "/api/v1/kumitegame/";
  private static final String GAME_COLLECTION = "kumiteGame";
  private static final Instant DECIDED = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private KumiteGameRepository games;

  @Autowired private TestRestTemplate anonymous;

  private String gameId;

  @BeforeEach
  void storeRunningMatch() {
    GameWithFighters match =
        KumiteGameBuilder.newGame()
            .runningSince(Instant.now().minusSeconds(10), Duration.ofMinutes(2))
            .buildWithFighters();
    match.fighters().values().forEach(mongoTemplate::save);
    gameId = games.save(match.game()).getId();
  }

  private static HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private ResponseEntity<String> put(TestRestTemplate client, String path, String body) {
    return client.exchange(ONE_GAME + gameId + path, HttpMethod.PUT, json(body), String.class);
  }

  private RefereeOverride storedOverride() {
    return games.findById(gameId).orElseThrow().getRefereeOverride().orElseThrow();
  }

  @Test
  void override_recordsWhoSignedInAndTheRefereeApart() {
    ResponseEntity<String> response =
        put(
            asAdmin(),
            "/override-winner",
            "{\"winner\":\"RED\",\"reason\":\"Stopped on the mat\",\"referee\":\"Sato\"}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storedOverride().operator()).isEqualTo(ADMIN);
    assertThat(storedOverride().referee()).isEqualTo("Sato");
  }

  /** The old field name, and the new one, both sent by a caller who wants to blame someone else. */
  @Test
  void override_cannotNameAnotherOperator() {
    put(
        asAdmin(),
        "/override-winner",
        "{\"winner\":\"RED\",\"reason\":\"Stopped\",\"referee\":\"Sato\","
            + "\"decidedBy\":\"someone-else\",\"operator\":\"someone-else\"}");

    assertThat(storedOverride().operator()).isEqualTo(ADMIN);
  }

  @Test
  void addedTime_recordsWhoSignedIn() {
    ResponseEntity<String> response =
        put(asScorekeeper(), "/add-time", "{\"seconds\":10,\"addedBy\":\"someone-else\"}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(games.findById(gameId).orElseThrow().getClockAdjustments())
        .singleElement()
        .satisfies(adjustment -> assertThat(adjustment.addedBy()).isEqualTo(SCOREKEEPER));
  }

  private void storeOverrideFromBeforeAccounts() {
    Document old =
        new Document("winner", "BLUE")
            .append("reason", "Stored long ago")
            .append("decidedBy", "Someone typed this")
            .append("decidedAt", Date.from(DECIDED))
            .append(
                "supersededOutcome",
                new Document("winner", "RED")
                    .append("reason", "POINTS_THRESHOLD")
                    .append("decidedBy", "HighestScoreWinnerRule"));
    mongoTemplate
        .getCollection(GAME_COLLECTION)
        .updateOne(
            new Document("_id", new ObjectId(gameId)),
            new Document(
                "$set",
                new Document("refereeOverride", old)
                    .append("winner", "BLUE")
                    .append("gameState", "FINISHED")
                    .append("endReason", "REFEREE_OVERRIDE")));
  }

  @Test
  void overrideFromBeforeAccounts_stillReads() {
    storeOverrideFromBeforeAccounts();

    ResponseEntity<KumiteGameResponse> response =
        anonymous.getForEntity(ONE_GAME + gameId, KumiteGameResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    KumiteGameResponse body = Objects.requireNonNull(response.getBody());
    assertThat(body.overridden()).isTrue();
    assertThat(body.winner()).isEqualTo(PlayerColor.BLUE);
    assertThat(storedOverride().reason()).isEqualTo("Stored long ago");
    assertThat(storedOverride().operator()).isNull();
    assertThat(storedOverride().referee()).isNull();
  }

  /** Stated in the models README: the old name has no field to go back into. */
  @Test
  void overrideFromBeforeAccounts_losesItsOldNameOnTheNextSave() {
    storeOverrideFromBeforeAccounts();

    games.save(games.findById(gameId).orElseThrow());

    Document stored =
        mongoTemplate
            .getCollection(GAME_COLLECTION)
            .find(new Document("_id", new ObjectId(gameId)))
            .first();
    assertThat(stored).isNotNull();
    assertThat(stored.get("refereeOverride", Document.class)).doesNotContainKey("decidedBy");
  }

  /** The old record's engine result must survive being overridden by a new one. */
  @Test
  void overrideOfAnOldOverride_keepsWhatTheEngineDecided() {
    storeOverrideFromBeforeAccounts();

    ResponseEntity<String> response =
        put(
            asAdmin(),
            "/override-winner",
            "{\"winner\":\"RED\",\"reason\":\"Corrected\",\"referee\":\"Sato\"}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storedOverride().operator()).isEqualTo(ADMIN);
    assertThat(storedOverride().supersededOutcome())
        .extracting(MatchOutcome::decidedBy)
        .isEqualTo("HighestScoreWinnerRule");
  }
}
