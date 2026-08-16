package com.management.testsupport;

import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import com.management.models.Foul;
import com.management.models.GameWithFighters;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Points;
import com.management.models.Referee;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds a {@link KumiteGame} for a test, stating only the field the test is about.
 *
 * <p>A default build is a valid queued match: exactly one RED fighter, exactly one BLUE fighter,
 * one referee, and the default duration. That default encodes the domain rule that a match has
 * exactly one of each colour, so no test has to know it and no test can violate it by accident.
 *
 * <p>Since #49 the match document stores fighter <em>ids</em>, not fighters. {@link #build()}
 * returns the match alone; {@link #buildWithFighters()} returns the {@link GameWithFighters}
 * composition the mapper and the slice tests consume, with each fighter copied so the built fixture
 * owns its score objects outright.
 *
 * <p>The default duration comes from {@link TestGameProperties}, the single place the test suite
 * states its duration values, so the number appears once rather than in every fixture. A test that
 * cares about a specific duration says so explicitly. Drift between those test values and the
 * shipped {@code application.properties} is caught by the integration suite, which binds the real
 * file.
 */
public final class KumiteGameBuilder {

  private static final Duration DEFAULT_DURATION = TestGameProperties.standard().defaultDuration();

  /**
   * A fixed instant, never {@code LocalDateTime.now()}.
   *
   * <p>Two reasons. A fixture that reads the clock is not deterministic, and a test that depends on
   * wall-clock time is required to be deleted rather than tolerated. And {@code now()} silently
   * resolves against the machine's default time-zone, which makes the value depend on where the
   * suite runs.
   */
  private static final LocalDateTime FIXED_START = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

  private final Map<PlayerColor, Player> fighters = new EnumMap<>(PlayerColor.class);
  private List<Referee> referees = List.of(new Referee("Test Referee"));
  private Duration gameDuration = DEFAULT_DURATION;
  private GameState gameState = GameState.QUEUED;
  @Nullable private Duration remainingTime;
  @Nullable private LocalDateTime startTime;
  @Nullable private PlayerColor winner;
  @Nullable private String id;

  private KumiteGameBuilder() {
    fighters.put(
        PlayerColor.RED,
        PlayerBuilder.newPlayer()
            .alreadyPersistedAs("red-fighter-id")
            .named("Red Fighter")
            .build());
    fighters.put(
        PlayerColor.BLUE,
        PlayerBuilder.newPlayer()
            .alreadyPersistedAs("blue-fighter-id")
            .named("Blue Fighter")
            .build());
  }

  /** A valid queued match: one RED, one BLUE, one referee, the standard default duration. */
  public static KumiteGameBuilder newGame() {
    return new KumiteGameBuilder();
  }

  /** Replaces the fighter of the given colour, keeping the one-of-each-colour rule intact. */
  public KumiteGameBuilder with(PlayerColor color, Player player) {
    fighters.put(color, player);
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
    return this;
  }

  /**
   * A match in progress whose clock started counting at the given instant.
   *
   * <p>For tests about elapsed time: the persisted {@code startTime} is what a rebuilt {@link
   * com.management.models.GameTimer} measures against, so a start instant a known offset in the
   * past yields a known amount of elapsed time without sleeping.
   */
  public KumiteGameBuilder runningSince(LocalDateTime since, Duration remaining) {
    this.gameState = GameState.RUNNING;
    this.remainingTime = remaining;
    this.startTime = since;
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
   * <p>Assigned reflectively because the id field is populated by the mapping layer through the
   * persistence constructor, and application code has no setter for it — deliberately.
   */
  public KumiteGameBuilder alreadyPersistedAs(String gameId) {
    this.id = gameId;
    return this;
  }

  /**
   * Copies a fighter so the built fixture owns it outright.
   *
   * <p>{@code Player} stores its {@code Points} and {@code Foul} by reference, so handing the same
   * instance to two fixtures gives them one shared score.
   */
  private static Player copyOf(Player source, String resolvedId) {
    Points points = new Points();
    points.setNumOfPoints(source.getPoints().getNumOfPoints());

    Foul fouls = new Foul();
    fouls.setNumOfFouls(source.getFouls().getNumOfFouls());

    return new Player(resolvedId, source.getName(), points, fouls);
  }

  /**
   * The fighter's id, invented if the caller's fighter has none.
   *
   * <p>The same value is used for the match's reference and for the copied fighter, so the two
   * halves of {@link #buildWithFighters()} always describe the same fighter. Resolving them
   * separately let a test build a match referring to {@code red-fighter-id} while its RED fighter
   * carried a null id, which serialised as {@code "id": null}.
   */
  private String fighterId(PlayerColor color) {
    String fighterId = fighters.get(color).getId();
    return fighterId != null ? fighterId : color.name().toLowerCase(Locale.ROOT) + "-fighter-id";
  }

  public KumiteGame build() {
    Map<PlayerColor, String> playerIds = new EnumMap<>(PlayerColor.class);
    fighters.keySet().forEach(color -> playerIds.put(color, fighterId(color)));

    KumiteGame game = new KumiteGame(playerIds, referees, gameDuration);
    game.setGameState(gameState);
    if (remainingTime != null) {
      game.setRemainingTime(remainingTime);
    }
    game.setStartTime(startTime);
    game.setWinner(winner);
    if (id != null) {
      ReflectionTestUtils.setField(game, "id", id);
    }
    return game;
  }

  /** The match composed with copies of its fighters, as the response mapper consumes it. */
  public GameWithFighters buildWithFighters() {
    Map<PlayerColor, Player> ownFighters = new EnumMap<>(PlayerColor.class);
    fighters.forEach((color, fighter) -> ownFighters.put(color, copyOf(fighter, fighterId(color))));
    return new GameWithFighters(build(), ownFighters);
  }
}
