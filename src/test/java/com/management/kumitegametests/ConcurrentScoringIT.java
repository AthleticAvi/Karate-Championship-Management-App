package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.dto.KumiteGameResponse;
import com.management.models.Player;
import com.management.repositories.PlayerRepository;
import com.management.testsupport.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The lost-update defect (#48), driven for real: genuinely concurrent scoring against one fighter
 * in a real MongoDB must end with every point recorded.
 *
 * <p>Sequential calls cannot reproduce the defect, so a latch releases all requests at once. Before
 * {@code @Version} landed, interleaved read-modify-write cycles silently swallowed points — the
 * final score was lower than the sum and nothing anywhere errored. With the version field every
 * stale write raises {@code OptimisticLockingFailureException}, and the {@code @Retryable} scoring
 * path re-reads and re-applies, so all writes land.
 */
class ConcurrentScoringIT extends IntegrationTestBase {

  private static final int CONCURRENT_SCORES = 6;

  @Autowired private TestRestTemplate rest;

  @Autowired private PlayerRepository playerRepository;

  @Test
  void concurrentYukoAwards_allLandOnTheScore() throws Exception {
    String gameId = createMatch();

    CountDownLatch start = new CountDownLatch(1);
    // Exchanged as String so a failed request reports its status and body instead of dying
    // in deserialisation of a problem-detail into the response record.
    Callable<ResponseEntity<String>> scoreOneYuko =
        () -> {
          start.await();
          return rest.exchange(
              "/api/kumitegame/" + gameId + "/add-point?color=RED&pointType=YUKO",
              HttpMethod.PUT,
              null,
              String.class);
        };

    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_SCORES);
    try {
      List<Future<ResponseEntity<String>>> results = new ArrayList<>();
      for (int i = 0; i < CONCURRENT_SCORES; i++) {
        results.add(pool.submit(scoreOneYuko));
      }
      start.countDown();

      for (Future<ResponseEntity<String>> result : results) {
        ResponseEntity<String> response = result.get(30, TimeUnit.SECONDS);
        assertThat(response.getStatusCode())
            .as("every concurrent award must succeed; body was: %s", response.getBody())
            .isEqualTo(HttpStatus.OK);
      }
    } finally {
      pool.shutdown();
    }

    ResponseEntity<KumiteGameResponse> finalState =
        rest.getForEntity("/api/kumitegame/" + gameId, KumiteGameResponse.class);
    assertThat(finalState.getBody()).isNotNull();
    assertThat(finalState.getBody().red().points())
        .as("every concurrent YUKO (1 point each) must be recorded — none silently lost")
        .isEqualTo(CONCURRENT_SCORES);
  }

  /** The mechanism itself: a save against a stale version must fail, not silently overwrite. */
  @Test
  void savingStaleFighter_raisesOptimisticLockConflict() {
    String gameId = createMatch();
    String redId =
        rest.getForEntity("/api/kumitegame/" + gameId, KumiteGameResponse.class)
            .getBody()
            .red()
            .id();

    Player firstRead = playerRepository.findById(redId).orElseThrow();
    Player secondRead = playerRepository.findById(redId).orElseThrow();

    firstRead.addFoul();
    playerRepository.save(firstRead);

    secondRead.addFoul();
    assertThatThrownBy(() -> playerRepository.save(secondRead))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  private String createMatch() {
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
    return response.getBody().id();
  }
}
