package com.management.kumitegame.web;

import com.management.kumitegame.domain.GameStatus;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.web.KumiteGameResponse.ParticipantResponse;
import org.jspecify.annotations.Nullable;

/**
 * A game in a list: enough to find it and see where it stands, and its id to read the rest.
 *
 * <p>Public as it is. It holds no entry, so nothing in it names who recorded what, raises a
 * warning, or carries free text.
 *
 * @param currentResult the result that stands; empty while none is recorded, after a reopen of a
 *     DECIDED game supersedes it, and on a VOIDED game
 */
public record KumiteGameSummaryResponse(
    String id,
    GameStatus status,
    SideSummaryResponse aka,
    SideSummaryResponse ao,
    @Nullable ResultSummaryResponse currentResult) {

  /**
   * One side.
   *
   * @param colour how the side is shown, from configuration
   * @param athlete the latest athlete put on this side; empty only in history that names none
   */
  public record SideSummaryResponse(String colour, @Nullable ParticipantResponse athlete) {}

  /**
   * A result as the officials decided it, without its free-text detail or who recorded it.
   *
   * @param winner only for a WIN
   */
  public record ResultSummaryResponse(
      Outcome outcome, @Nullable Side winner, ResultReason reason, boolean bothDisqualified) {}
}
