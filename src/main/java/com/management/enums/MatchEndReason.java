package com.management.enums;

/**
 * Why a match ended.
 *
 * <p>Recorded on the match, because the result alone does not explain itself: a decided match and a
 * forfeit look identical from the winner's colour. Every ending condition names one of these, and a
 * match that has ended has exactly one — the first condition to fire wins, and a later one never
 * overwrites it.
 */
public enum MatchEndReason {
  /** A fighter crossed the winning score. */
  POINTS_THRESHOLD,
  /** A fighter accumulated the configured number of fouls. */
  FOUL_LIMIT,
  /** The clock reached zero. */
  TIME_EXPIRED,
  /** A referee disqualified a fighter. */
  DISQUALIFICATION,
  /** A fighter did not appear, or withdrew — KIKEN. */
  KIKEN,
  /** A referee ended the match themselves, overriding the rules engine. */
  REFEREE_OVERRIDE
}
