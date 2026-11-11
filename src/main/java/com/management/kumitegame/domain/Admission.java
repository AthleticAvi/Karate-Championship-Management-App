package com.management.kumitegame.domain;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** One cell of the status matrix: applied, applied with a warning, or refused. */
record Admission(boolean refused, @Nullable Warning warning) {

  static final Admission ALLOW = new Admission(false, null);
  static final Admission REFUSE = new Admission(true, null);

  static Admission warn(Warning warning) {
    return new Admission(false, warning);
  }

  /** Refuses the action, or adds this cell's warning to what it raised. */
  void apply(EntryKind kind, GameStatus status, List<Warning> raised) {
    if (refused) {
      throw new ActionRefusedException(
          "A game that is "
              + status
              + " accepts no "
              + kind.name().toLowerCase(Locale.ROOT).replace('_', ' ')
              + ".");
    }
    if (warning != null) {
      raised.add(warning);
    }
  }
}
