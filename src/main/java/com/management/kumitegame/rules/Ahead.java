package com.management.kumitegame.rules;

import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.SideState;
import com.management.kumitegame.domain.Standing;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Which side is ahead on one count, such as points or IPPON. */
final class Ahead {

  private Ahead() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /** The side with more, or empty when level. */
  static Optional<Side> on(Standing standing, ToIntFunction<SideState> count) {
    int lead = lead(standing, count);
    if (lead == 0) {
      return Optional.empty();
    }
    return Optional.of(lead > 0 ? Side.AKA : Side.AO);
  }

  /** AKA's count less AO's. */
  static int lead(Standing standing, ToIntFunction<SideState> count) {
    return count.applyAsInt(standing.side(Side.AKA)) - count.applyAsInt(standing.side(Side.AO));
  }
}
