package com.management.kumitegame.domain;

/**
 * Where a game stands. Never stored: folded from the entries, in order.
 *
 * <p>APPROVED and VOIDED are final: nothing is recorded on them but an Admin's reopen of an
 * APPROVED game, or a void of one.
 */
public enum GameStatus {
  NOT_STARTED,
  FIGHTING,
  TIME_UP,
  DECIDED,
  /** The result is signed off. */
  APPROVED,
  /** Annulled. The record stays. */
  VOIDED;

  /** Where the main clock takes a game until a result stands. */
  boolean isMovedByTheClock() {
    return this == NOT_STARTED || this == FIGHTING || this == TIME_UP;
  }

  /** Nothing is recorded on a final game but an Admin's reopen of an APPROVED game, or a void. */
  boolean isFinal() {
    return this == APPROVED || this == VOIDED;
  }
}
