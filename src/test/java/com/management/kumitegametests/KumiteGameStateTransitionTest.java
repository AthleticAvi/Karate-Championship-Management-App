package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.management.enums.GameState;
import com.management.exceptions.IllegalStateTransitionException;
import com.management.models.KumiteGame;
import com.management.repositories.KumiteGameRepository;
import com.management.services.KumiteGameService;
import com.management.services.PlayerService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.KumiteGameBuilder;
import com.management.testsupport.TestGameProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The transition guards on the four lifecycle methods (#29).
 *
 * <p>One legal and one illegal transition per method. The full matrix lives on the enum and is
 * asserted in {@code GameStateTest}; here the point is that each service method actually consults
 * it, refuses with the exception the handler maps to 409, and leaves the stored match untouched
 * when it refuses.
 */
class KumiteGameStateTransitionTest {

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
  void startGame_fromQueued_isLegal() {
    String gameId = save(KumiteGameBuilder.newGame().build());

    assertThat(service.startGame(gameId).getGameState()).isEqualTo(GameState.RUNNING);
  }

  @Test
  void startGame_onFinishedMatch_isRejectedNamingStateAndAction() {
    String gameId = save(KumiteGameBuilder.newGame().finished().build());

    assertThatThrownBy(() -> service.startGame(gameId))
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining("FINISHED")
        .hasMessageContaining("started");
  }

  /**
   * The verb is narrower than the state machine: PAUSED → RUNNING is a legal transition, but the
   * way to make it is {@code resumeGame} — a paused match cannot be <em>started</em>.
   */
  @Test
  void startGame_onPausedMatch_isRejected() {
    String gameId =
        save(KumiteGameBuilder.newGame().pausedWithRemaining(Duration.ofSeconds(45)).build());

    assertThatThrownBy(() -> service.startGame(gameId))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  /** The mirror case: a queued match has never run, so there is nothing to resume. */
  @Test
  void resumeGame_onQueuedMatch_isRejected() {
    String gameId = save(KumiteGameBuilder.newGame().build());

    assertThatThrownBy(() -> service.resumeGame(gameId))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void pauseGame_whileRunning_isLegal() {
    String gameId =
        save(KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(90)).build());

    assertThat(service.pauseGame(gameId).getGameState()).isEqualTo(GameState.PAUSED);
  }

  @Test
  void pauseGame_onQueuedMatch_isRejected() {
    String gameId = save(KumiteGameBuilder.newGame().build());

    assertThatThrownBy(() -> service.pauseGame(gameId))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void resumeGame_fromPaused_isLegal() {
    String gameId =
        save(KumiteGameBuilder.newGame().pausedWithRemaining(Duration.ofSeconds(45)).build());

    assertThat(service.resumeGame(gameId).getGameState()).isEqualTo(GameState.RUNNING);
  }

  @Test
  void resumeGame_whileAlreadyRunning_isRejected() {
    String gameId =
        save(KumiteGameBuilder.newGame().runningWithRemaining(Duration.ofSeconds(90)).build());

    assertThatThrownBy(() -> service.resumeGame(gameId))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void endGame_whilePaused_isLegal() {
    String gameId =
        save(KumiteGameBuilder.newGame().pausedWithRemaining(Duration.ofSeconds(45)).build());

    assertThat(service.endGame(gameId).getGameState()).isEqualTo(GameState.FINISHED);
  }

  /**
   * The transition #29 singles out: ending a queued match zeroes a clock that never started, so a
   * later start would begin with no time on it. The guard must also leave the stored match exactly
   * as it was.
   */
  @Test
  void endGame_onQueuedMatch_isRejectedAndChangesNothing() {
    KumiteGame queued = storage.save(KumiteGameBuilder.newGame().build());

    assertThatThrownBy(() -> service.endGame(queued.getId()))
        .isInstanceOf(IllegalStateTransitionException.class);

    KumiteGame stored = storage.findById(KumiteGame.class, queued.getId()).orElseThrow();
    assertThat(stored.getGameState()).isEqualTo(GameState.QUEUED);
    assertThat(stored.getRemainingTime())
        .as("the clock was not zeroed by the rejected call")
        .isEqualTo(queued.getRemainingTime());
  }

  private String save(KumiteGame game) {
    return storage.save(game).getId();
  }
}
