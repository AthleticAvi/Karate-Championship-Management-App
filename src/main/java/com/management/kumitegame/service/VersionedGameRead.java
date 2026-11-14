package com.management.kumitegame.service;

import com.management.kumitegame.domain.GameRead;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The version of every stored record a game's read shows, and the read itself, built only when
 * asked: the game, and each athlete and referee it names that still exists. Each version comes from
 * the same read as what it describes, so a tag built from them changes whenever the read could.
 *
 * <p><strong>The fold waits for {@link #read()}.</strong> The records are already read, so a caller
 * that only needs the versions, such as one answering 304, never folds the history.
 *
 * <p>The athlete and referee versions stay out of their aggregate types: see {@code [pattern]} in
 * {@code src/main/java/com/management/referee/README.md}. The game's is here as well as on the
 * read, because a 304 needs it without folding.
 *
 * @param athleteVersions by athlete id; one deleted in the accepted race is absent
 * @param refereeVersions by referee id; one deleted in the accepted race is absent
 * @param fold folds the records already read into the game as read, at the time it is called
 */
public record VersionedGameRead(
    long gameVersion,
    Map<String, Long> athleteVersions,
    Map<String, Long> refereeVersions,
    Supplier<GameRead> fold) {

  public VersionedGameRead {
    athleteVersions = Map.copyOf(athleteVersions);
    refereeVersions = Map.copyOf(refereeVersions);
  }

  /**
   * Folds the history and names the athletes and referees: the work a 304 does not need. Each call
   * folds again, at a new server time, so a caller asks once.
   */
  public GameRead read() {
    return fold.get();
  }
}
