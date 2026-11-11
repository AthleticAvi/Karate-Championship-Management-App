package com.management.kumitegame.service;

import com.management.kumitegame.domain.GameRead;
import java.util.Map;

/**
 * A game as read, with the version of every stored record it shows: the game, and each athlete and
 * referee it names that still exists. Each version comes from the same read as what it describes,
 * so a tag built from them changes whenever the read could.
 *
 * <p>The versions stay out of the domain types: see {@code [pattern]} in {@code
 * src/main/java/com/management/referee/README.md}.
 *
 * @param athleteVersions by athlete id; one deleted in the accepted race is absent
 * @param refereeVersions by referee id; one deleted in the accepted race is absent
 */
public record VersionedGameRead(
    GameRead read,
    long gameVersion,
    Map<String, Long> athleteVersions,
    Map<String, Long> refereeVersions) {

  public VersionedGameRead {
    athleteVersions = Map.copyOf(athleteVersions);
    refereeVersions = Map.copyOf(refereeVersions);
  }
}
