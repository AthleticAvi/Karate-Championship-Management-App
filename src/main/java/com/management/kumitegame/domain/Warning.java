package com.management.kumitegame.domain;

/**
 * A rule breach the system can see. The action is applied all the same, the warning is stored on
 * its entry and returned: officials may know better.
 */
public enum Warning {
  /** The time the screen showed is further from the derived clock than the tolerance allows. */
  TIME_SHOWN_OUT_OF_TOLERANCE,
  /** The clock remaining sent with a score, penalty or SENSHU record is outside the tolerance. */
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
  /** A result other than the one the rules suggested. Recorded as given. */
  RESULT_DIFFERS_FROM_SUGGESTION,
  /**
   * A correction while DECIDED made the result that stands, which agreed with the suggestion, no
   * longer agree. Nothing reopens.
   */
  SUGGESTION_DIFFERS_FROM_RESULT,
  /** SENSHU recorded before the start. */
  SENSHU_BEFORE_START,
  /** SENSHU recorded while a result stands. */
  SENSHU_AFTER_RESULT,
  /**
   * SENSHU awarded when the other side scored in the same exchange, or such a score recorded after
   * the award (Articles 12.2.2, 12.2.9).
   */
  SENSHU_WHEN_BOTH_SCORED,
  /** SENSHU withdrawn from a side that does not hold it: nothing changes. */
  SENSHU_WITHDRAWN_NOT_HELD,
  /** SENSHU awarded after a withdrawal in the final window closed it (Article 12.2.10). */
  SENSHU_AFTER_CLOSED,
  /**
   * The holder penalised for avoiding combat in the final window, so SENSHU is forfeit (Articles
   * 12.2.8, 10.4.16).
   */
  SENSHU_HOLDER_AVOIDING_COMBAT,
  /** A cancelled score leaves the holder with no unopposed score (Article 12.2.2). */
  SENSHU_HOLDER_WITHOUT_UNOPPOSED_SCORE,
  /** A medal game in round robin, or an extra game in elimination. */
  FORMAT_COMBINATION,
  /** The result rules failed on this game, so it has no suggestion. The failure is logged. */
  SUGGESTION_UNAVAILABLE,
  /** Stored history this build cannot fold as written. The entry is skipped. */
  HISTORY_INCONSISTENT
}
