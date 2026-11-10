package com.management.kumitegame.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  /** Paris moved its clocks forward an hour at this instant. */
  private static final Instant SPRING_CHANGE = Instant.parse("2026-03-29T01:00:00Z");

  /** Paris moved its clocks back an hour at this instant. */
  private static final Instant AUTUMN_CHANGE = Instant.parse("2026-10-25T01:00:00Z");

  private static Clock fixedAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  @Test
  void getRemainingTime_whileRunning_subtractsTheElapsedTime() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(120), NOON, fixedAt(NOON.plusSeconds(30)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void getRemainingTime_whenTheClockRanPastTheDuration_reportsZeroNotNegative() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(1), NOON, fixedAt(NOON.plusSeconds(15)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ZERO);
  }

  @Test
  void pause_whileRunning_banksTheElapsedTimeAndStopsCounting() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(120), NOON, fixedAt(NOON.plusSeconds(45)));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(75));
  }

  @Test
  void pause_whenTheClockRanPastTheDuration_banksZeroNotNegative() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(1), NOON, fixedAt(NOON.plusSeconds(15)));

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
    GameTimer timer = new GameTimer(Duration.ofSeconds(90), NOON, fixedAt(NOON.minusSeconds(600)));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void pause_whenTheHostClockSteppedBackwards_banksTheUnchangedTime() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(90), NOON, fixedAt(NOON.minusSeconds(600)));

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
    GameTimer timer = new GameTimer(Duration.ofSeconds(120), NOON, fixedAt(NOON.plusSeconds(30)));

    timer.resume();

    assertThat(timer.getRemainingTime())
        .as("the original start instant still counts; resume did not reset it")
        .isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void stop_zeroesTheClock() {
    GameTimer timer = new GameTimer(Duration.ofSeconds(120), NOON, fixedAt(NOON.plusSeconds(30)));

    timer.stop();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ZERO);
  }

  /** Wall clocks jump an hour here, so local times would show 61 minutes for a 60 second bout. */
  @Test
  void getRemainingTime_acrossTheSpringClockChange_countsOnlyRealElapsedTime() {
    GameTimer timer =
        new GameTimer(
            Duration.ofSeconds(120),
            SPRING_CHANGE.minusSeconds(30),
            Clock.fixed(SPRING_CHANGE.plusSeconds(30), PARIS));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(60));
  }

  /** Wall clocks repeat an hour here, so local times would show the bout running backwards. */
  @Test
  void getRemainingTime_acrossTheAutumnClockChange_countsOnlyRealElapsedTime() {
    GameTimer timer =
        new GameTimer(
            Duration.ofSeconds(120),
            AUTUMN_CHANGE.minusSeconds(30),
            Clock.fixed(AUTUMN_CHANGE.plusSeconds(30), PARIS));

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void pause_acrossTheSpringClockChange_banksOnlyRealElapsedTime() {
    GameTimer timer =
        new GameTimer(
            Duration.ofSeconds(120),
            SPRING_CHANGE.minusSeconds(30),
            Clock.fixed(SPRING_CHANGE.plusSeconds(30), PARIS));

    timer.pause();

    assertThat(timer.getRemainingTime()).isEqualTo(Duration.ofSeconds(60));
  }
}
