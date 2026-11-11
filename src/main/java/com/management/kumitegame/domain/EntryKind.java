package com.management.kumitegame.domain;

import static com.management.kumitegame.domain.Admission.ALLOW;
import static com.management.kumitegame.domain.Admission.REFUSE;
import static com.management.kumitegame.domain.Admission.warn;

/**
 * Which kinds of action each status accepts: the status matrix of the planning record, row by row.
 *
 * <p>Refuse only the impossible; warn where a rule is breached but officials may know better. Two
 * cells depend on more than the status, and the action applies them: a score in TIME_UP warns only
 * after the late-score window, and a result before the start warns unless it is KIKEN or SHIKKAKU.
 *
 * <p>The matrix is a method and not constructor arguments, so the constants hold no state.
 */
enum EntryKind {
  SCORE,
  PENALTY,
  CLOCK_ACTION,
  AMENDMENT,
  ASSIGNMENT,
  SETTINGS_CHANGE,
  RESULT;

  /** The cell for this kind of action in this status. */
  Admission admission(GameStatus status) {
    return switch (this) {
      case SCORE ->
          row(
              status,
              warn(Warning.SCORE_BEFORE_START),
              ALLOW,
              ALLOW,
              warn(Warning.SCORE_AFTER_RESULT));
      case PENALTY, AMENDMENT -> ALLOW;
      case CLOCK_ACTION ->
          row(status, ALLOW, ALLOW, ALLOW, warn(Warning.CLOCK_ACTION_AFTER_RESULT));
      case ASSIGNMENT ->
          status == GameStatus.NOT_STARTED ? ALLOW : warn(Warning.ASSIGNMENT_AFTER_START);
      case SETTINGS_CHANGE -> status == GameStatus.NOT_STARTED ? ALLOW : REFUSE;
      case RESULT ->
          row(
              status,
              warn(Warning.RESULT_BEFORE_START),
              ALLOW,
              ALLOW,
              warn(Warning.RESULT_WHILE_DECIDED));
    };
  }

  private static Admission row(
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
    };
  }
}
