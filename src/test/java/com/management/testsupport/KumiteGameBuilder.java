package com.management.testsupport;

import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import com.management.util.GameConfig;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds a {@link KumiteGame} for a test, stating only the field the test is about.
 *
 * <p>A default build is a valid queued match: exactly one RED fighter, exactly one BLUE fighter,
 * one referee, and the default duration read from configuration. That default encodes the domain
 * rule that a match has exactly one of each colour, so no test has to know it and no test can
 * violate it by accident.
 *
 * <p>The duration comes from {@link GameConfig} rather than a literal, because the project rule is
 * that durations are never hardcoded. A test that cares about a specific duration says so
 * explicitly.
 *
 * <p>Never returns a shared instance: each {@link #build()} produces a new object.
 */
public final class KumiteGameBuilder {

  private static final GameConfig CONFIG = new GameConfig();

  /**
   * A fixed instant, never {@code LocalDateTime.now()}.
   *
   * <p>Two reasons. A fixture that reads the clock is not deterministic, and a test that depends on
   * wall-clock time is required to be deleted rather than tolerated. And {@code now()} silently
   * resolves against the machine's default time-zone, which makes the value depend on where the
   * suite runs.
   */
  private static final LocalDateTime FIXED_START = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

  private final Map<PlayerColor, Player> players = new EnumMap<>(PlayerColor.class);
  private List<Referee> referees = List.of(new Referee("Test Referee"));
  private Duration gameDuration = CONFIG.getDefaultDuration();
  private GameState gameState = GameState.QUEUED;
  @Nullable private Duration remainingTime;
  @Nullable private LocalDateTime startTime;
  @Nullable private PlayerColor winner;
  @Nullable private String id;
  private boolean timerInitialized;

  private KumiteGameBuilder() {
    players.put(PlayerColor.RED, PlayerBuilder.newPlayer().named("Red Fighter").build());
    players.put(PlayerColor.BLUE, PlayerBuilder.newPlayer().named("Blue Fighter").build());
  }

  /** A valid queued match: one RED, one BLUE, one referee, default duration from config. */
  public static KumiteGameBuilder newGame() {
    return new KumiteGameBuilder();
  }

  /** Replaces the fighter of the given colour, keeping the one-of-each-colour rule intact. */
  public KumiteGameBuilder with(PlayerColor color, Player player) {
    players.put(color, player);
    return this;
  }

  public KumiteGameBuilder refereedBy(Referee... officials) {
    this.referees = List.of(officials);
    return this;
  }

  /** Sets the match length. Use only when the test is about the duration itself. */
  public KumiteGameBuilder lasting(Duration duration) {
    this.gameDuration = duration;
    return this;
  }

  /** A match in progress, with the given time left on the clock. */
  public KumiteGameBuilder runningWithRemaining(Duration remaining) {
    this.gameState = GameState.RUNNING;
    this.remainingTime = remaining;
    this.startTime = FIXED_START;
    this.timerInitialized = true;
    return this;
  }

  /** A match stopped mid-bout, with the given time left on the clock. */
  public KumiteGameBuilder pausedWithRemaining(Duration remaining) {
    this.gameState = GameState.PAUSED;
    this.remainingTime = remaining;
    this.startTime = null;
    return this;
  }

  /** A match that has ended, with no time left. */
  public KumiteGameBuilder finished() {
    this.gameState = GameState.FINISHED;
    this.remainingTime = Duration.ZERO;
    return this;
  }

  /** A match decided in favour of the given colour. */
  public KumiteGameBuilder wonBy(PlayerColor color) {
    this.winner = color;
    return this;
  }

  /**
   * Gives the match a persistent identifier, as though it had been saved.
   *
   * <p>Assigned reflectively because {@link KumiteGame} has no constructor that takes an id and no
   * setter for one — the mapping layer populates the field directly. #52 adds an explicit
   * persistence constructor, at which point this can call it instead.
   */
  public KumiteGameBuilder alreadyPersistedAs(String gameId) {
    this.id = gameId;
    return this;
  }

  /**
   * Gives the match a live timer object, as {@code startGame} and {@code resumeGame} do.
   *
   * <p>Rarely wanted directly. The timer is {@code @Transient}, so a match loaded from storage
   * never has one — a test that sets it here is asserting behaviour that only holds before a
   * reload.
   */
  public KumiteGameBuilder withLiveTimer() {
    this.timerInitialized = true;
    return this;
  }

  public KumiteGame build() {
    KumiteGame game = new KumiteGame(new EnumMap<>(players), referees, gameDuration);
    game.setGameState(gameState);
    if (remainingTime != null) {
      game.setRemainingTime(remainingTime);
    }
    game.setStartTime(startTime);
    game.setWinner(winner);
    if (id != null) {
      ReflectionTestUtils.setField(game, "id", id);
    }
    if (timerInitialized) {
      game.initializeTimer(game.getRemainingTime());
    }
    return game;
  }
}
