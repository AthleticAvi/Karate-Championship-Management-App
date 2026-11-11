package com.management.kumitegame.domain;

/** Why a game ended as it did, recorded as the officials decided it (Article 12). */
public enum ResultReason {
  LEAD,
  POINTS,
  SENSHU,
  IPPON,
  WAZA_ARI,
  HANTEI,
  HIKIWAKE,
  HANSOKU,
  SHIKKAKU,
  KIKEN,
  DOUBLE_DISQUALIFICATION;

  /** May end a game that never started: an athlete absent or disqualified before it. */
  public boolean isBeforeTheStart() {
    return this == KIKEN || this == SHIKKAKU;
  }
}
