package com.management.kumitegame.domain;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a game stands, as the result rules read it: folded from the entries, never stored. What
 * stands now, not the order it happened in.
 *
 * @param settings the game's snapshot: the rules read their values only from here
 * @param timeUp the game is TIME_UP, or was when its result was recorded and the clock has not put
 *     time back since. It holds while DECIDED
 * @param senshuHolder the side holding SENSHU, if any
 */
public record Standing(
    GameFormat format,
    boolean medalGame,
    boolean extraGame,
    Settings settings,
    boolean timeUp,
    Map<Side, SideState> sides,
    @Nullable Side senshuHolder) {

  public Standing {
    sides = Map.copyOf(sides);
  }

  public SideState side(Side side) {
    return Objects.requireNonNull(sides.get(side));
  }
}
