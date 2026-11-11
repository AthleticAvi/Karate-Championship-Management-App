package com.management.kumitegame.domain;

/**
 * The two sides of a game. Rules, scoring and storage use these, never a colour.
 *
 * <p>The colour a side is shown in is configuration, mapped at the gateway: WKF Kumite Competition
 * Rules 2026.01, Article 2.2 and Appendix 1 dress AKA in red and AO in blue.
 */
public enum Side {
  AKA,
  AO;

  public Side opponent() {
    return this == AKA ? AO : AKA;
  }
}
