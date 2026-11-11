package com.management.kumitegame.domain;

import java.util.List;

/**
 * What an action did: the warnings it raised, and whether it was a retry of one already applied.
 *
 * <p>A retry applies nothing and answers the warnings stored with the first, never recomputed.
 */
public record Recorded(List<Warning> raisedWarnings, boolean replayed) {

  public Recorded {
    raisedWarnings = List.copyOf(raisedWarnings);
  }

  static Recorded applied(List<Warning> raisedWarnings) {
    return new Recorded(raisedWarnings, false);
  }

  static Recorded replayOf(EntryMeta stored) {
    return new Recorded(stored.raisedWarnings(), true);
  }
}
