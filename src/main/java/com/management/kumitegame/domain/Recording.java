package com.management.kumitegame.domain;

import java.util.List;

/** What a mutation answers: the game as it now stands, and the warnings the action raised. */
public record Recording(GameRead game, List<Warning> raisedWarnings) {

  public Recording {
    raisedWarnings = List.copyOf(raisedWarnings);
  }
}
