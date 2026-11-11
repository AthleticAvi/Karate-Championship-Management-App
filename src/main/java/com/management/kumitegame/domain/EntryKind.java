package com.management.kumitegame.domain;

import static com.management.kumitegame.domain.Admission.ALLOW;
import static com.management.kumitegame.domain.Admission.REFUSE;
import static com.management.kumitegame.domain.Admission.warn;

/**
 * Which kinds of action each status accepts: the status matrix of the planning record, row by row.
 *
 * <p>Refuse only the impossible; warn where a rule is breached but officials may know better. Some
 * cells depend on more than the status, and the action applies them: a score in TIME_UP warns only
 * after the late-score window, a result before the start warns unless it is KIKEN or SHIKKAKU, and
 * a reopen of an APPROVED game and every void need an Admin and a reason.
 *
 * <p>APPROVED and VOIDED are final: every kind is refused there but a reopen of an APPROVED game,
 * and a void of one.
 *
 * <p>The matrix is a method and not constructor arguments, so the constants hold no state.
 */
enum EntryKind {
  SCORE,
  PENALTY,
  CLOCK_ACTION,
  AMENDMENT,
  SENSHU_RECORD,
  ASSIGNMENT,
  SETTINGS_CHANGE,
  RESULT,
  APPROVE,
  REOPEN,
  VOID;

  /** The kind of a status move. */
  static EntryKind of(StatusChangeKind kind) {
    return switch (kind) {
      case APPROVE -> APPROVE;
      case REOPEN -> REOPEN;
      case VOID -> VOID;
    };
  }

  /** The cell for this kind of action in this status. */
  Admission admission(GameStatus status) {
    return switch (this) {
      case SCORE ->
          recording(
              status,
              warn(Warning.SCORE_BEFORE_START),
              ALLOW,
              ALLOW,
              warn(Warning.SCORE_AFTER_RESULT));
      case PENALTY, AMENDMENT -> recording(status, ALLOW, ALLOW, ALLOW, ALLOW);
      case SENSHU_RECORD ->
          recording(
              status,
              warn(Warning.SENSHU_BEFORE_START),
              ALLOW,
              ALLOW,
              warn(Warning.SENSHU_AFTER_RESULT));
      case CLOCK_ACTION ->
          recording(status, ALLOW, ALLOW, ALLOW, warn(Warning.CLOCK_ACTION_AFTER_RESULT));
      case ASSIGNMENT -> {
        Admission afterStart = warn(Warning.ASSIGNMENT_AFTER_START);
        yield recording(status, ALLOW, afterStart, afterStart, afterStart);
      }
      case SETTINGS_CHANGE -> recording(status, ALLOW, REFUSE, REFUSE, REFUSE);
      case RESULT ->
          recording(
              status,
              warn(Warning.RESULT_BEFORE_START),
              ALLOW,
              ALLOW,
              warn(Warning.RESULT_WHILE_DECIDED));
      case APPROVE -> status == GameStatus.DECIDED ? ALLOW : REFUSE;
      case REOPEN -> status == GameStatus.DECIDED || status == GameStatus.APPROVED ? ALLOW : REFUSE;
      case VOID -> status == GameStatus.VOIDED ? REFUSE : ALLOW;
    };
  }

  /** A row for an action that records what happened: refused on a final game. */
  private static Admission recording(
      GameStatus status,
      Admission notStarted,
      Admission fighting,
      Admission timeUp,
      Admission decided) {
    return switch (status) {
      case NOT_STARTED -> notStarted;
      case FIGHTING -> fighting;
      case TIME_UP -> timeUp;
      case DECIDED -> decided;
      case APPROVED, VOIDED -> REFUSE;
    };
  }
}
