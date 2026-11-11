package com.management.kumitegame.domain;

/** How a game ended. Only WIN has a winner. DRAW is a round-robin result (Article 12.2.4). */
public enum Outcome {
  WIN,
  DRAW,
  NO_RESULT
}
