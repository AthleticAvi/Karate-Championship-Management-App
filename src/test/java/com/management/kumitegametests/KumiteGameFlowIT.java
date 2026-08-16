package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;

import com.management.dto.KumiteGameResponse;
import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * One match from creation to a decided result, over real HTTP against a real database.
 *
 * <p><strong>What only this layer can catch.</strong> The slice tests mock the services, so they
 * prove the web layer's contract and nothing about whether the layers fit together. Scoring in this
 * application crosses two aggregates — the point lands on a {@code Player} document and is then
 * re-synced into the copy embedded in the match — and that path involves two repositories, a
 * circular service dependency broken by {@code @Lazy}, and a save-and-reload round trip. It can
 * only fail with all of that present.
 *
 * <p><strong>Deliberately one test, not a suite.</strong> A full-context test costs seconds where a
 * slice costs milliseconds, so this covers the one flow that matters end to end and leaves
 * enumeration of status codes and bodies to the slices, where it belongs.
 *
 * <p>The response is deserialised into the real {@link KumiteGameResponse} record, which means this
 * also proves the contract is round-trippable by a client using the same shape — the record has no
 * default constructor, so a field the server stops sending would surface here.
 */
class KumiteGameFlowIT extends IntegrationTestBase {

  @Autowired private TestRestTemplate rest;

  @Test
  void matchLifecycle_fromCreationToDecision_isScoredStoredAndReadBack() {
    KumiteGameResponse created = createMatch();
    assertThat(created.gameState()).isEqualTo(GameState.QUEUED);
    assertThat(created.remainingSeconds()).isEqualTo(90);
    assertThat(created.winner()).as("a new match has no winner").isNull();

    String gameId = created.id();

    KumiteGameResponse afterRedScored =
        put("/api/kumitegame/" + gameId + "/add-point?color=RED&pointType=IPPON");
    assertThat(afterRedScored.red().points()).isEqualTo(3);
    assertThat(afterRedScored.blue().points()).isZero();

    KumiteGameResponse afterBlueScored =
        put("/api/kumitegame/" + gameId + "/add-point?color=BLUE&pointType=YUKO");
    assertThat(afterBlueScored.blue().points()).isEqualTo(1);
    assertThat(afterBlueScored.red().points())
        .as("scoring for one fighter must not disturb the other")
        .isEqualTo(3);

    KumiteGameResponse afterFoul = put("/api/kumitegame/" + gameId + "/add-foul?color=BLUE");
    assertThat(afterFoul.blue().fouls()).isEqualTo(1);

    KumiteGameResponse decided = put("/api/kumitegame/" + gameId + "/update-winner/RED");
    assertThat(decided.winner()).isEqualTo(PlayerColor.RED);

    ResponseEntity<KumiteGameResponse> readBack =
        rest.getForEntity("/api/kumitegame/" + gameId, KumiteGameResponse.class);
    assertThat(readBack.getStatusCode()).isEqualTo(HttpStatus.OK);
    KumiteGameResponse finalState = readBack.getBody();
    assertThat(finalState).isNotNull();
    assertThat(finalState.winner())
        .as("everything scored during the match survived being written and reloaded")
        .isEqualTo(PlayerColor.RED);
    assertThat(finalState.red().points()).isEqualTo(3);
    assertThat(finalState.blue().points()).isEqualTo(1);
    assertThat(finalState.blue().fouls()).isEqualTo(1);
    assertThat(finalState.referees()).containsExactly("Referee One");
  }

  @Test
  void scoring_whenTheColourIsUnrecognised_isRejectedAsBadInput() {
    String gameId = createMatch().id();

    ResponseEntity<String> response =
        rest.exchange(
            "/api/kumitegame/" + gameId + "/add-point?color=purple&pointType=IPPON",
            HttpMethod.PUT,
            null,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isNotNull()
        .satisfies(
            type ->
                assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .as("error bodies are served as application/problem+json, not plain JSON")
                    .isTrue());
  }

  /**
   * A malformed match is refused before anything is written.
   *
   * <p>Two fighters both claiming RED used to be accepted: both {@code Player} documents and the
   * half-formed match were saved, and only then did the response mapper notice the missing BLUE and
   * raise a 500. The caller was told the server had failed, got no id back, and three orphaned
   * documents stayed in the database — with no transaction to undo them, since this MongoDB is a
   * standalone.
   *
   * <p>Asserting the status alone would not have caught that. What matters is the count afterwards.
   */
  @Test
  void createMatchRequest_withTwoFightersOfOneColour_isRejectedAndWritesNothing() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String bothRed =
        """
        {
          "playersMap": {
            "red":  { "name": "Kenji", "color": "RED" },
            "blue": { "name": "Sato",  "color": "RED" }
          },
          "refereeList": ["Referee One"],
          "gameDuration": 90
        }
        """;

    ResponseEntity<String> response =
        rest.postForEntity("/api/kumitegame", new HttpEntity<>(bothRed, headers), String.class);

    assertThat(response.getStatusCode())
        .as("a request that cannot produce a valid match is the caller's mistake, not a 500")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mongoTemplate.getCollection("kumiteGame").countDocuments())
        .as("no match was stored")
        .isZero();
    assertThat(mongoTemplate.getCollection("player").countDocuments())
        .as("and no fighter was left orphaned by the rejected request")
        .isZero();
  }

  private KumiteGameResponse createMatch() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body =
        """
        {
          "playersMap": {
            "red":  { "name": "Kenji", "color": "RED" },
            "blue": { "name": "Sato",  "color": "BLUE" }
          },
          "refereeList": ["Referee One"],
          "gameDuration": 90
        }
        """;

    ResponseEntity<KumiteGameResponse> response =
        rest.postForEntity(
            "/api/kumitegame", new HttpEntity<>(body, headers), KumiteGameResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    KumiteGameResponse created = response.getBody();
    assertThat(created).isNotNull();
    assertThat(created.id()).isNotNull();
    return created;
  }

  private KumiteGameResponse put(String path) {
    ResponseEntity<KumiteGameResponse> response =
        rest.exchange(path, HttpMethod.PUT, null, KumiteGameResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    KumiteGameResponse body = response.getBody();
    assertThat(body).isNotNull();
    return body;
  }
}
