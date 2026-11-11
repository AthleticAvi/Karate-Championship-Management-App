package com.management.kumitegame.domain;

/**
 * Whether a game's format fields make sense together: a medal game is an elimination game, and an
 * extra game is a round-robin tie-break (Appendix 6).
 *
 * <p>Derived rather than raised by an action, because the game is created with these fields and
 * creation is not an entry.
 */
final class FormatCombination {

  private FormatCombination() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  static boolean isOdd(GameFormat format, boolean medalGame, boolean extraGame) {
    return (format == GameFormat.ROUND_ROBIN && medalGame)
        || (format == GameFormat.ELIMINATION && extraGame);
  }
}
