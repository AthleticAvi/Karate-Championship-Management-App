package com.management.referee.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A referee, kept once and outside any match. A match refers to one by id.
 *
 * <p>The domain type: what the business knows, and nothing of how it is stored. The stored shape is
 * {@code com.management.referee.data.RefereeDocument}: see {@code
 * src/main/java/com/management/referee/README.md}.
 *
 * @param id null until the referee is stored, which assigns it
 */
public record Referee(@Nullable String id, String name) {

  public Referee {
    Objects.requireNonNull(name, "a referee needs a name");
  }

  /** A referee not yet stored. */
  public static Referee named(String name) {
    return new Referee(null, name);
  }

  public Referee rename(String newName) {
    return new Referee(id, newName);
  }
}
