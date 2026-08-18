package com.management.enums;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The penalty stages a fighter passes through, in order of severity.
 *
 * <p><strong>Derived, never stored.</strong> A fighter's stage is a function of their foul count —
 * {@link #forCount(int)} — so the two cannot disagree. Storing the stage alongside the count would
 * be two records of one fact, which is the defect epic #47 spent its whole length removing.
 *
 * <p>The two later stages carry consequences beyond a warning: {@link #HANSOKU} awards points to
 * the opponent, and {@link #SHIKKAKU} disqualifies. Whether either is reachable by accumulation
 * depends on {@code game.fouls-ending-match}: at the default of 4 the match ends at {@link
 * #HANSOKU_CHUI}, so the later stages arrive only through a referee's explicit disqualification.
 */
public enum FoulTypes {
  CHUI1,
  CHUI2,
  CHUI3,
  HANSOKU_CHUI,
  HANSOKU(PointsType.IPPON),
  SHIKKAKU;

  private final @Nullable PointsType opponentAward;

  FoulTypes() {
    this(null);
  }

  FoulTypes(@Nullable PointsType opponentAward) {
    this.opponentAward = opponentAward;
  }

  /**
   * The stage a fighter is at after {@code fouls} fouls, or empty when they have none.
   *
   * <p>Counts beyond the last stage stay at the last stage: the progression cannot escalate past
   * disqualification, and a count that high means the match should already have ended.
   */
  public static Optional<FoulTypes> forCount(int fouls) {
    if (fouls <= 0) {
      return Optional.empty();
    }
    FoulTypes[] stages = values();
    return Optional.of(stages[Math.min(fouls, stages.length) - 1]);
  }

  /** The points this stage hands the opponent, or empty when it is a warning only. */
  public Optional<PointsType> opponentAward() {
    return Optional.ofNullable(opponentAward);
  }

  /** Whether reaching this stage puts the fighter out of the match. */
  public boolean isDisqualifying() {
    return this == SHIKKAKU;
  }
}
