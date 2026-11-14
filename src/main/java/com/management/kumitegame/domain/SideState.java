package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * One side, derived from the entries: never stored. Cancelled scores and penalties do not count.
 *
 * @param athleteId the latest athlete put on this side
 */
public record SideState(
    @Nullable String athleteId,
    int total,
    int ipponCount,
    int wazaAriCount,
    int chuiCount,
    int hansokuChuiCount,
    int hansokuCount,
    int shikkakuCount) {

  /**
   * A standing HANSOKU or SHIKKAKU disqualifies the side from the game (Articles 10.3, 10.4). The
   * penalties alone, as the result rules read them: {@link GameState#isDisqualified(Side)} adds the
   * result that stands.
   */
  public boolean isDisqualified() {
    return hansokuCount > 0 || shikkakuCount > 0;
  }
}
