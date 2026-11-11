package com.management.kumitegame.domain;

/**
 * A rule breach the system can see. The action is applied all the same, the warning is stored on
 * its entry and returned: officials may know better.
 */
public enum Warning {
  /** The time the screen showed is further from the derived clock than the tolerance allows. */
  TIME_SHOWN_OUT_OF_TOLERANCE,
  /** The clock remaining sent with a score or penalty is outside the tolerance. */
  CLOCK_REMAINING_OUT_OF_TOLERANCE,
  SCORE_BEFORE_START,
  /** A score in TIME_UP later than the late-score window allows (Article 8.10). */
  SCORE_AFTER_LATE_SCORE_WINDOW,
  SCORE_AFTER_RESULT,
  /** A CHUI past the limit (Article 10.3). */
  CHUI_OVER_LIMIT,
  /** HANSOKU on a side with no HANSOKU_CHUI before it (Article 10.3). */
  HANSOKU_WITHOUT_HANSOKU_CHUI,
  CLOCK_ACTION_AFTER_RESULT,
  /** An athlete or panel change after the start. */
  ASSIGNMENT_AFTER_START,
  /** A result before the start, other than KIKEN or SHIKKAKU. */
  RESULT_BEFORE_START,
  /** A result while one stands: it supersedes the current one. */
  RESULT_WHILE_DECIDED,
  /** A medal game in round robin, or an extra game in elimination. */
  FORMAT_COMBINATION,
  /** Stored history this build cannot fold as written. The entry is skipped. */
  HISTORY_INCONSISTENT
}
