package com.management.enums;

import static com.management.enums.GameState.FINISHED;
import static com.management.enums.GameState.PAUSED;
import static com.management.enums.GameState.QUEUED;
import static com.management.enums.GameState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The full transition matrix, as one table: everything not listed as legal is illegal. */
class GameStateTest {

  private static Set<GameState> legalTargetsOf(GameState from) {
    return switch (from) {
      case QUEUED -> Set.of(RUNNING);
      case RUNNING -> Set.of(PAUSED, FINISHED);
      case PAUSED -> Set.of(RUNNING, FINISHED);
      case FINISHED -> Set.of();
    };
  }

  @ParameterizedTest
  @EnumSource(GameState.class)
  void canTransitionTo_matchesTheSettledStateMachine(GameState from) {
    for (GameState to : GameState.values()) {
      assertThat(from.canTransitionTo(to))
          .as("%s -> %s", from, to)
          .isEqualTo(legalTargetsOf(from).contains(to));
    }
  }
}
