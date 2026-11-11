package com.management.athlete.service;

import com.management.athlete.domain.Athlete;

/**
 * An athlete with the version it is stored at, read together, for a reader that must know when it
 * changes, such as a game's entity tag.
 *
 * <p>The version stays out of the domain type: see {@code [pattern]} in {@code
 * src/main/java/com/management/referee/README.md}.
 */
public record VersionedAthlete(Athlete athlete, long version) {}
