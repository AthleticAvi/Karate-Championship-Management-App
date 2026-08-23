package com.management.testsupport;

import com.management.enums.PointsType;
import com.management.models.Foul;
import com.management.models.Player;
import com.management.models.Points;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds a {@link Player} for a test, stating only the field the test is about.
 *
 * <p>A default build is a valid fighter with a name, no points and no fouls. Every method names an
 * intent rather than a field assignment, so a failure message describes the scenario.
 *
 * <p>Never returns a shared instance: each {@link #build()} produces a new object <em>and new score
 * objects</em>, because a shared mutable fixture reintroduces the order dependence the suite is
 * required not to have.
 *
 * <p>That second half used to be untrue. The builder held one {@code Points} and one {@code Foul}
 * and handed the same two references to every {@code Player} it built — and {@code Player}'s
 * constructor stores them without copying. Two players from one builder therefore shared a score:
 * awarding a point to one moved the other, and calling {@link #scoring} after {@code build()}
 * changed a player that had already been built. The intent is recorded as {@code times} and {@code
 * fouls} counts now, and the mutable objects are created inside {@code build()}, so nothing a
 * builder holds can be reached by anything it produced.
 */
public final class PlayerBuilder {

  @Nullable private String id;
  private String name = "Test Fighter";
  private final Map<PointsType, Integer> scored = new EnumMap<>(PointsType.class);
  private int fouls;

  private PlayerBuilder() {}

  /** A valid fighter with no points and no fouls. */
  public static PlayerBuilder newPlayer() {
    return new PlayerBuilder();
  }

  public PlayerBuilder named(String playerName) {
    this.name = playerName;
    return this;
  }

  /** Gives the player a persistent identifier, as though it had been saved. */
  public PlayerBuilder alreadyPersistedAs(String playerId) {
    this.id = playerId;
    return this;
  }

  /** Awards a point of the given type, applying the same strategy production uses. */
  public PlayerBuilder scoring(PointsType pointsType) {
    return scoring(pointsType, 1);
  }

  /** Awards the given point type {@code times} times. */
  public PlayerBuilder scoring(PointsType pointsType, int times) {
    scored.merge(pointsType, times, Integer::sum);
    return this;
  }

  /** Records {@code count} fouls against the player. */
  public PlayerBuilder withFouls(int count) {
    this.fouls += count;
    return this;
  }

  /**
   * Builds a player whose score objects belong to it alone.
   *
   * <p>The points are replayed through the production strategies rather than set directly, so the
   * fixture still cannot disagree with how scoring actually works.
   */
  public Player build() {
    Points playerPoints = new Points();
    scored.forEach(
        (pointsType, times) -> {
          for (int i = 0; i < times; i++) {
            pointsType.getStrategy().addPoint(playerPoints);
          }
        });

    Foul playerFouls = new Foul();
    for (int i = 0; i < fouls; i++) {
      playerFouls.addFoul();
    }

    return new Player(id, name, playerPoints, playerFouls);
  }
}
