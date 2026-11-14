package com.management.service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A domain value with the version it is stored at, read together, for a reader that must know when
 * it changes, such as a game's entity tag.
 *
 * <p>The version stays out of the domain type: see {@code [pattern]} in {@code
 * src/main/java/com/management/referee/README.md}.
 */
public record Versioned<T>(T value, long version) {

  /** The values of a map of versioned values, by the same keys. */
  public static <T> Map<String, T> values(Map<String, Versioned<T>> byId) {
    return byId.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().value()));
  }

  /** The versions of a map of versioned values, by the same keys. */
  public static <T> Map<String, Long> versions(Map<String, Versioned<T>> byId) {
    return byId.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().version()));
  }
}
