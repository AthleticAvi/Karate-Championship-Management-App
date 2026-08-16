package com.management.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The clock arithmetic, made deterministic with a fixed {@link Clock}.
 *
 * <p>No sleeping: a timer whose {@code startTime} sits a chosen offset before the injected clock's
 * instant has exactly that much elapsed time. The clamp at zero (#26) and the
 * reconstruction-from-persisted-state contract (#25) are both pinned here.
 */
class GameTimerTest {

  private static final Instant NOON = Instant.parse("2026-01-01T12:00:00Z");
  private static final LocalDateTime NOON_LOCAL = LocalDateTime.ofInstant(NOON, ZoneOffset.UTC);

  private static Clock fixedAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  @Test
  void getRemainingTime_whileRunning_subtractsTheElapsedTime() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(120), NOON_LOCAL, fixedAt(NOON.plusSeconds(30)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void getRemainingTime_whenTheClockRanPastTheDuration_reportsZeroNotNegative() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(1), NOON_LOCAL, fixedAt(NOON.plusSeconds(15)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ZERO);
  }

  @Test
  void pause_whileRunning_banksTheElapsedTimeAndStopsCounting() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(120), NOON_LOCAL, fixedAt(NOON.plusSeconds(45)));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(75));
  }

  @Test
  void pause_whenTheClockRanPastTheDuration_banksZeroNotNegative() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(1), NOON_LOCAL, fixedAt(NOON.plusSeconds(15)));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ZERO);
  }

  /**
   * The trap #25 documents: a timer rebuilt without its {@code startTime} treats {@code pause()} as
   * a no-op. This pins that behaviour so the reconstruction contract — restore both values — stays
   * visible.
   */
  @Test
  void pause_whenTheTimerIsNotCounting_changesNothing() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(90), null, fixedAt(NOON));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  /** A host clock stepped backwards (NTP, DST) must never add time to the match. */
  @Test
  void getRemainingTime_whenTheHostClockSteppedBackwards_doesNotGrowTheClock() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(90), NOON_LOCAL, fixedAt(NOON.minusSeconds(600)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void pause_whenTheHostClockSteppedBackwards_banksTheUnchangedTime() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(90), NOON_LOCAL, fixedAt(NOON.minusSeconds(600)));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void resume_whenPaused_startsCountingFromTheBankedTime() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(75), null, fixedAt(NOON));

    timer.resume();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(75));
  }

  @Test
  void resume_whileAlreadyCounting_doesNotRestartTheClock() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(120), NOON_LOCAL, fixedAt(NOON.plusSeconds(30)));

    timer.resume();

    assertThat(timer.getRemainingTime())
        .as("the original start instant still counts; resume did not reset it")
        .isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void stop_zeroesTheClock() {
    GameTimer timer =
        new GameTimer(Duration.ofSeconds(120), NOON_LOCAL, fixedAt(NOON.plusSeconds(30)));

    timer.stop();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ZERO);
  }
}
