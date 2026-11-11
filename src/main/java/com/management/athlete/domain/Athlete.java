package com.management.athlete.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An athlete, kept once and outside any game. A game refers to one by id and looks the name up when
 * it is read: it never copies it.
 *
 * <p>The domain type: what the business knows, and nothing of how it is stored. The stored shape is
 * {@code com.management.athlete.data.AthleteDocument}: see {@code
 * src/main/java/com/management/athlete/README.md}. Keeps no list of games: a search finds them.
 *
 * @param weightKg the weight in kilograms, when known
 */
public record Athlete(String id, String name, @Nullable Double weightKg) {

  public Athlete {
    Objects.requireNonNull(id, "an athlete has an id");
    Objects.requireNonNull(name, "an athlete needs a name");
  }

  public Athlete update(String newName, @Nullable Double newWeightKg) {
    return new Athlete(id, newName, newWeightKg);
  }
}
