package com.management.kumitegame.domain;

/**
 * A move of the status that no other entry causes: sign a result off, reopen a game, or void one.
 * Every other status move is the side effect of the entry that causes it.
 */
public enum StatusChangeKind {
  /** DECIDED to APPROVED: the result is signed off, and the game is final. */
  APPROVE,
  /**
   * DECIDED back to the status before the result, which it supersedes; or, by an Admin, APPROVED
   * back to DECIDED.
   */
  REOPEN,
  /** Any status but VOIDED to VOIDED, by an Admin: the game is annulled, and the record stays. */
  VOID;

  /**
   * Only an Admin may take this move from this status, and must give a reason: reopening an
   * APPROVED game (WKF Kumite Competition Rules 2026.01, Articles 13.4, 10.4.17), and voiding.
   */
  boolean isAdminOnly(GameStatus from) {
    return this == VOID || (this == REOPEN && from == GameStatus.APPROVED);
  }
}
