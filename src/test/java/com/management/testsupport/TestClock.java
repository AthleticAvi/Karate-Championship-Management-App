package com.management.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The time the fast suite runs at, so no test depends on when or where it runs.
 *
 * <p>A test about elapsed time stores a start a known offset before {@link #NOW}. One about a later
 * read rebuilds the timer with a clock of its own.
 */
public final class TestClock {

  public static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  public static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

  private TestClock() {}
}
