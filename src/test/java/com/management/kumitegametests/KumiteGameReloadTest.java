package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.management.enums.GameState;
import com.management.models.KumiteGame;
import com.management.repositories.KumiteGameRepository;
import com.management.services.KumiteGameService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The game lifecycle exercised across a real save-and-reload boundary.
 *
 * <p>{@code GameTimer} is {@code @Transient}, so a game fetched from storage never has one. Every
 * lifecycle method fetches before it acts, which makes the reload the interesting part rather than
 * an incidental detail.
 *
 * <p>Two tests here are {@code @Disabled} because they assert the behaviour #25 will introduce. To
 * watch them fail against the current code, run them with the disabling condition switched off:
 *
 * <pre>
 * mvn test -Dtest=KumiteGameReloadTest \
 *          -DargLine="-Djunit.jupiter.conditions.deactivate=*"
 * </pre>
 *
 * <p>Both fail with a {@code NullPointerException} on the return value of {@code
 * KumiteGame.getTimer()} being null — {@code pause()} for the first, {@code stop()} for the second,
 * since {@code endGame} stops the timer rather than pausing it. That is #25 exactly.
 */
class KumiteGameReloadTest {

  private InMemoryMongo storage;
  private KumiteGameService service;
  private String gameId;

  @BeforeEach
  void setUp() {
    storage = new InMemoryMongo();
    KumiteGameRepository repository = FakeRepositories.kumiteGames(storage);

    service = new KumiteGameService();
    ReflectionTestUtils.setField(service, "kumiteGameRepository", repository);

    gameId = storage.save(KumiteGameBuilder.newGame().build()).getId();
  }

  @Test
  void startGame_setsTheGameRunning() {
    KumiteGame started = service.startGame(gameId);

    assertThat(started.getGameState()).isEqualTo(GameState.RUNNING);
    assertThat(started.getStartTime()).isNotNull();
  }

  @Test
  void startGame_thenReload_leavesNoTimerOnTheStoredGame() {
    service.startGame(gameId);

    KumiteGame reloaded = storage.findById(KumiteGame.class, gameId).orElseThrow();

    assertThat(reloaded.getGameState())
        .as("the running state is persisted")
        .isEqualTo(GameState.RUNNING);
    assertThat(reloaded.getTimer()).as("but the timer is not, because it is @Transient").isNull();
  }

  @Test
  @Disabled(
      "Fails until #25 is fixed. pauseGame calls getTimer().pause() on a game it has just fetched"
          + " from storage, where the @Transient timer is always null. Delete this annotation as"
          + " part of #25 and this becomes its regression test.")
  void pauseGame_afterTheGameWasReloaded_pausesWithoutCrashing() {
    service.startGame(gameId);

    assertThatCode(() -> service.pauseGame(gameId)).doesNotThrowAnyException();

    KumiteGame paused = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(paused.getGameState()).isEqualTo(GameState.PAUSED);
    assertThat(paused.getStartTime()).isNull();
  }

  @Test
  @Disabled(
      "Fails until #25 is fixed. endGame has the same defect as pauseGame: it dereferences the"
          + " transient timer on a freshly fetched game. Delete this annotation as part of #25.")
  void endGame_afterTheGameWasReloaded_finishesWithoutCrashing() {
    service.startGame(gameId);

    assertThatCode(() -> service.endGame(gameId)).doesNotThrowAnyException();

    KumiteGame finished = storage.findById(KumiteGame.class, gameId).orElseThrow();
    assertThat(finished.getGameState()).isEqualTo(GameState.FINISHED);
  }
}
