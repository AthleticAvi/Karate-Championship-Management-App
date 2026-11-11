package com.management.kumitegame.domain;

import com.management.athlete.domain.Athlete;
import com.management.referee.domain.Referee;
import java.time.Instant;
import java.util.Map;

/**
 * A game as read: the game, what its entries derive, and the athletes and referees it names, looked
 * up at read time and never copied into it.
 *
 * <p>An athlete or referee the game names but that no longer exists is simply absent from its map,
 * and reads as unknown: the accepted race between creating a game and deleting one it names.
 *
 * @param serverTime the server's time when it was read, so a screen's countdown does not drift
 */
public record GameRead(
    KumiteGame game,
    GameState state,
    Map<String, Athlete> athletes,
    Map<String, Referee> referees,
    Instant serverTime) {

  public GameRead {
    athletes = Map.copyOf(athletes);
    referees = Map.copyOf(referees);
  }
}
