package com.management.models;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * The match clock: a countdown that runs while {@code startTime} is set and holds still while it is
 * {@code null}.
 *
 * <p>Never persisted — {@link KumiteGame} stores the two values that describe the clock ({@code
 * remainingTime} and {@code startTime}) and rebuilds this object from them on demand. Rebuilding
 * with only the remaining time silently freezes the clock: {@link #pause()} does nothing when
 * {@code startTime} is {@code null}, so a reconstruction that drops a live {@code startTime} turns
 * a running clock into a stopped one with no error. Both values must survive the round trip, which
 * is why the reconstruction constructor takes both.
 *
 * <p>Time is read through an injected {@link Clock} so elapsed-time behaviour is testable without
 * sleeping. Production uses the system clock.
 */
public class GameTimer {
  private final Clock clock;
  @Nullable private LocalDateTime startTime;
  private Duration remainingTime;

  /** A fresh clock with the full duration on it, not yet running. */
  public GameTimer(Duration initialDuration) {
    this(initialDuration, null, Clock.systemDefaultZone());
  }

  /**
   * Reconstructs the clock from persisted state.
   *
   * @param remainingTime the time left when {@code startTime} was last set, or when the clock last
   *     paused
   * @param startTime when the clock last started counting, or {@code null} if it is not counting
   */
  public GameTimer(Duration remainingTime, @Nullable LocalDateTime startTime) {
    this(remainingTime, startTime, Clock.systemDefaultZone());
  }

  GameTimer(Duration remainingTime, @Nullable LocalDateTime startTime, Clock clock) {
    this.remainingTime = remainingTime;
    this.startTime = startTime;
    this.clock = clock;
  }

  public void start() {
    this.startTime = LocalDateTime.now(clock);
  }

  public void pause() {
    if (startTime != null) {
      this.remainingTime = clampToZero(remainingTime.minus(elapsedSinceStart(startTime)));
      this.startTime = null;
    }
  }

  public void resume() {
    if (startTime == null) {
      this.startTime = LocalDateTime.now(clock);
    }
  }

  public void stop() {
    this.remainingTime = Duration.ZERO;
    this.startTime = null;
  }

  /**
   * Stops the clock, reporting the time that was left on it.
   *
   * <p>For a match that ends before its time is up — a threshold crossing, a forfeit. {@link
   * #stop()} zeroes the clock, which makes an early finish indistinguishable from one that ran the
   * full duration; this keeps the evidence.
   */
  public Duration stopAndReport() {
    Duration left = getRemainingTime();
    this.remainingTime = left;
    this.startTime = null;
    return left;
  }

  /**
   * Adds time to the clock, whether it is running or paused.
   *
   * <p>Adding to a <em>running</em> clock is the case that goes wrong: the remaining time is only
   * meaningful together with the instant it was last measured from, so the elapsed time so far is
   * banked first and the clock restarted from now. Adding to the total without doing that would
   * hand back the added time and then immediately subtract the same elapsed period again.
   *
   * @param extra a positive duration; zero or negative is rejected, because removing time is a
   *     different operation with different rules and is deliberately not offered here
   */
  public void add(Duration extra) {
    if (extra.isNegative() || extra.isZero()) {
      throw new IllegalArgumentException(
          "Time added to the clock must be positive, but was " + extra);
    }
    if (startTime != null) {
      this.remainingTime = clampToZero(remainingTime.minus(elapsedSinceStart(startTime)));
      this.startTime = LocalDateTime.now(clock);
    }
    this.remainingTime = remainingTime.plus(extra);
  }

  /** Whether the clock is currently counting down. */
  public boolean isRunning() {
    return startTime != null;
  }

  /** The instant the clock last started counting, or {@code null} if it is not running. */
  public @Nullable LocalDateTime startedAt() {
    return startTime;
  }

  /** Time left on the clock, never negative: a match that ran long reports zero, not minus. */
  public Duration getRemainingTime() {
    if (startTime != null) {
      return clampToZero(remainingTime.minus(elapsedSinceStart(startTime)));
    }
    return remainingTime;
  }

  /**
   * Elapsed time since the clock started counting, never negative: a host clock stepped backwards
   * must not add time to the match.
   */
  private Duration elapsedSinceStart(LocalDateTime since) {
    return clampToZero(Duration.between(since, LocalDateTime.now(clock)));
  }

  private static Duration clampToZero(Duration duration) {
    return duration.isNegative() ? Duration.ZERO : duration;
  }
}
