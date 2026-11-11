package com.management.kumitegame.domain;

/**
 * Where a game stands. Never stored: folded from the entries, in order.
 *
 * <p>APPROVED and VOIDED arrive with sign-off.
 */
public enum GameStatus {
  NOT_STARTED,
  FIGHTING,
  TIME_UP,
  DECIDED
}
