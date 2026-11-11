package com.management.kumitegame.domain;

/**
 * A side's score for the game as its format counts it: in round robin after a forfeit, not what it
 * scored (Articles 10.7.1, 6.2, 12.3.2). Otherwise what it scored. Derived, never stored.
 *
 * @param total the points
 * @param ipponCount the IPPON that count
 * @param wazaAriCount the WAZA_ARI that count
 * @param addedPoints points given rather than scored, which count as YUKO; points, not a number of
 *     YUKO
 */
public record EffectiveScore(int total, int ipponCount, int wazaAriCount, int addedPoints) {

  /** What the side scored, as it scored it. */
  static EffectiveScore earned(SideState side) {
    return new EffectiveScore(side.total(), side.ipponCount(), side.wazaAriCount(), 0);
  }

  /** The score of a side whose score is set to zero. */
  static EffectiveScore zero() {
    return new EffectiveScore(0, 0, 0, 0);
  }
}
