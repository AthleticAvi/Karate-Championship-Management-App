package com.management.kumitegame.domain;

import java.util.List;

/**
 * Warns on a penalty that breaks the ladder of Article 10.3: a CHUI past the limit, or HANSOKU with
 * no HANSOKU_CHUI before it. Never refuses: the referee may skip levels, and the level is recorded
 * as given.
 */
final class PenaltyLadder {

  private PenaltyLadder() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  static void check(
      SideState offender, PenaltyLevel level, Settings settings, List<Warning> raised) {
    if (level == PenaltyLevel.CHUI && offender.chuiCount() + 1 > settings.chuiLimit()) {
      raised.add(Warning.CHUI_OVER_LIMIT);
    }
    if (level == PenaltyLevel.HANSOKU && offender.hansokuChuiCount() == 0) {
      raised.add(Warning.HANSOKU_WITHOUT_HANSOKU_CHUI);
    }
  }
}
