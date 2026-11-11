package com.management.referee.domain;

import java.util.Objects;

/**
 * A referee, kept once and outside any game. A game refers to one by id.
 *
 * <p>The domain type: what the business knows, and nothing of how it is stored. The stored shape is
 * {@code com.management.referee.data.RefereeDocument}: see {@code
 * src/main/java/com/management/referee/README.md}. Always a stored referee: storing a new one is
 * what gives it an id.
 */
public record Referee(String id, String name) {

  public Referee {
    Objects.requireNonNull(id, "a referee has an id");
    Objects.requireNonNull(name, "a referee needs a name");
  }

  public Referee rename(String newName) {
    return new Referee(id, newName);
  }
}
