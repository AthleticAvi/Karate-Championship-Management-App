package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.management.enums.GameState;
import com.management.models.KumiteGame;
import com.management.repositories.KumiteGameRepository;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.TestGameProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The game lifecycle exercised across a real save-and-reload boundary.
 *
 * <p>{@code GameTimer} is {@code @Transient}, so a game fetched from storage never carries one —
 * {@code KumiteGame.getTimer()} rebuilds it from the persisted {@code remainingTime} and {@code
 * startTime}. Every lifecycle method fetches before it acts, which makes the reload the interesting
 * part rather than an incidental detail.
 *
 * <p>This class replaced {@code KumiteGameTimerTest}, whose repository stub handed back the same
 * in-memory instance on every read. That stub preserved the transient timer across "reloads", so
 * all five of its tests passed against code that crashed on the first real pause (#25, #27).
 *
 * <p>Elapsed-time behaviour is asserted without sleeping: a match saved with a {@code startTime} a
 * known offset in the past has a known amount of elapsed time the moment it is paused. The exact
 * clock arithmetic, including the clamp at zero, is unit-tested in {@code GameTimerTest} with an
 * injected clock; here the point is that the values survive persistence.
 */
class KumiteGameReloadTest {

  private static final Duration FULL_MATCH = Duration.ofSeconds(120);

  private InMemoryMongo storage;
  private KumiteGameService service;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();
    KumiteGameRepository repository = FakeRepositories.kumiteGames(storage);

    service =
        new KumiteGameService(
            repository, TestGameProperties.standard(), Mockito.mock(PlayerService.class));
  }

  @Test
  void startGame_setsTheGameRunning() {
    String gameId = saveQueuedGame();

    KumiteGame started = service.startGame(gameId);

    assertThat(started.getGameState()).isEqualTo(GameState.RUNNING);
    assertThat(started.getStartTime()).isNotNull();
    assertThat(started.getRemainingTime())
        .as("remainingTime is defined as the time left when startTime was set — exactly full")
        .isEqualTo(FULL_MATCH);
  }

  @Test
  void startGame_thenReload_leavesNoTimerInTheStoredDocument() {
    String gameId = saveQueuedGame();

    service.startGame(gameId);

    KumiteGame reloaded = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(reloaded.getGameState())
        .as("the running state is persisted")
        .isEqualTo(GameState.RUNNING);
    assertThat(storage.writeForInspection(reloaded).containsKey("timer"))
        .as("the timer object itself is @Transient and never stored")
        .isFalse();
  }

  @Test
  void pauseGame_afterTheGameWasReloaded_pausesWithoutCrashing() {
    String gameId = saveQueuedGame();
    service.startGame(gameId);

    assertThatCode(() -> service.pauseGame(gameId)).doesNotThrowAnyException();

    KumiteGame paused = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(paused.getGameState()).isEqualTo(GameState.PAUSED);
    assertThat(paused.getStartTime()).isNull();
  }

  @Test
  void endGame_afterTheGameWasReloaded_finishesWithoutCrashing() {
    String gameId = saveQueuedGame();
    service.startGame(gameId);

    assertThatCode(() -> service.endGame(gameId)).doesNotThrowAnyException();

    KumiteGame finished = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(finished.getGameState()).isEqualTo(GameState.FINISHED);
    assertThat(finished.getRemainingTime()).isEqualTo(Duration.ZERO);
    assertThat(finished.getStartTime())
        .as("a finished match has no running-clock marker left behind")
        .isNull();
  }

  /**
   * The proof that the rebuilt clock actually counted down.
   *
   * <p>The naive fix for #25 — rebuilding the timer from {@code remainingTime} alone — passes every
   * crash test and silently freezes the clock, because a timer with no {@code startTime} treats
   * {@code pause()} as a no-op. This test fails against that fix: the persisted remaining time must
   * have decreased by roughly the time the match has been running.
   */
  @Test
  void pauseGame_whenTheMatchHasRunForThirtySeconds_persistsTheElapsedTime() {
    String gameId =
        storage
            .save(
                KumiteGameBuilder.newGame()
                    .runningSince(LocalDateTime.now().minusSeconds(30), FULL_MATCH)
                    .build())
            .getId();

    service.pauseGame(gameId);

    KumiteGame paused = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(paused.getRemainingTime())
        .as("about thirty seconds elapsed, with slack for a slow machine")
        .isBetween(Duration.ofSeconds(85), Duration.ofSeconds(90));
  }

  @Test
  void pauseGame_thenResume_countsDownAgainFromWhereItPaused() {
    String gameId =
        storage
            .save(
                KumiteGameBuilder.newGame()
                    .runningSince(LocalDateTime.now().minusSeconds(30), FULL_MATCH)
                    .build())
            .getId();
    final Duration atPause = service.pauseGame(gameId).getRemainingTime();

    service.resumeGame(gameId);

    KumiteGame resumed = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(resumed.getGameState()).isEqualTo(GameState.RUNNING);
    assertThat(resumed.getStartTime()).as("the clock is counting again").isNotNull();
    assertThat(resumed.getRemainingTime())
        .as("resuming does not give time back or take extra away")
        .isEqualTo(atPause);
  }

  /** #26 at the service level: a match left running past its own duration reports zero. */
  @Test
  void pauseGame_whenTheMatchRanLongPastItsDuration_persistsZeroNotNegative() {
    String gameId =
        storage
            .save(
                KumiteGameBuilder.newGame()
                    .runningSince(LocalDateTime.now().minusHours(2), FULL_MATCH)
                    .build())
            .getId();

    service.pauseGame(gameId);

    KumiteGame paused = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(paused.getRemainingTime()).isEqualTo(Duration.ZERO);
  }

  private String saveQueuedGame() {
    return storage.save(KumiteGameBuilder.newGame().build()).getId();
  }
}
