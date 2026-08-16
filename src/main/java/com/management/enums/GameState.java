package com.management.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle of a match, with its legal transitions held as data.
 *
 * <p>The state machine is settled domain: {@code QUEUED → RUNNING ⇄ PAUSED}, with {@code FINISHED}
 * reachable from {@code RUNNING} and {@code PAUSED} only. A finished match is terminal. Keeping the
 * rule on the enum, as data rather than as scattered {@code if} statements, means the services
 * enforcing it and the planned Game Orchestrator consulting it read the same single table.
 */
public enum GameState {
  QUEUED,
  RUNNING,
  PAUSED,
  FINISHED;

  private static final Map<GameState, Set<GameState>> LEGAL_TRANSITIONS =
      Map.of(
          QUEUED, EnumSet.of(RUNNING),
          RUNNING, EnumSet.of(PAUSED, FINISHED),
          PAUSED, EnumSet.of(RUNNING, FINISHED),
          FINISHED, EnumSet.noneOf(GameState.class));

  /** Whether a match in this state may move to {@code target}. */
  public boolean canTransitionTo(GameState target) {
    return LEGAL_TRANSITIONS.get(this).contains(target);
  }
}
