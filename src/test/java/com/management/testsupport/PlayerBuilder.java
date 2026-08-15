package com.management.testsupport;

import com.management.enums.PointsType;
import com.management.models.Foul;
import com.management.models.Player;
import com.management.models.Points;
import org.springframework.lang.Nullable;

/**
 * Builds a {@link Player} for a test, stating only the field the test is about.
 *
 * <p>A default build is a valid fighter with a name, no points and no fouls. Every method names an
 * intent rather than a field assignment, so a failure message describes the scenario.
 *
 * <p>Never returns a shared instance: each {@link #build()} produces a new object, because a shared
 * mutable fixture reintroduces the order dependence the suite is required not to have.
 */
public final class PlayerBuilder {

  @Nullable private String id;
  private String name = "Test Fighter";
  private final Points points = new Points();
  private final Foul fouls = new Foul();

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
    pointsType.getStrategy().addPoint(points);
    return this;
  }

  /** Awards the given point type {@code times} times. */
  public PlayerBuilder scoring(PointsType pointsType, int times) {
    for (int i = 0; i < times; i++) {
      scoring(pointsType);
    }
    return this;
  }

  /** Records {@code count} fouls against the player. */
  public PlayerBuilder withFouls(int count) {
    for (int i = 0; i < count; i++) {
      fouls.addFoul();
    }
    return this;
  }

  public Player build() {
    return new Player(id, name, points, fouls);
  }
}
