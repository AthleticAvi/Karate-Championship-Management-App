package com.management.config;

import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.Settings;
import com.management.kumitegame.domain.Side;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The rule values a new game is created with, and how each side is shown, bound from {@code
 * kumite-game.*} and validated at startup: a bad value stops the application.
 *
 * <p>Each new game copies {@link Rules#snapshot()} and from then on reads only its copy, so a value
 * changed here and redeployed reaches new games only.
 *
 * @param sideColours the colour each side is shown in; mapped at the gateway, never used by rules
 */
@Validated
@ConfigurationProperties("kumite-game")
public record KumiteGameProperties(
    @Valid @NotNull Rules rules, @NotNull Map<Side, @NotBlank String> sideColours) {

  @AssertTrue(message = "every side needs a colour")
  public boolean isEverySideColoured() {
    return sideColours != null && sideColours.keySet().containsAll(EnumSet.allOf(Side.class));
  }

  /**
   * One row per setting of the planning record's settings table. Durations bind from values such as
   * {@code 120s} and are copied into a game as whole milliseconds.
   */
  public record Rules(
      @NotNull @DurationMin(millis = 1) Duration duration,
      @NotEmpty List<@NotNull @DurationMin(millis = 1) Duration> durationsOffered,
      @NotNull Map<ScoreLevel, @NotNull @Positive Integer> pointValues,
      @Positive int leadToWin,
      @NotNull @DurationMin(millis = 1) Duration finalWindow,
      @NotNull @DurationMin(millis = 0) Duration lateScoreWindow,
      @Positive int chuiLimit,
      @Positive int roundRobinForfeitPoints,
      @NotEmpty List<@NotNull @DurationMin(millis = 1) Duration> addTimeSteps,
      @NotNull @DurationMin(millis = 1) Duration medicalTime,
      @NotNull @DurationMin(millis = 0) Duration timeShownTolerance,
      @Positive int panelLimit,
      @NotBlank String rulebookVersion) {

    @AssertTrue(message = "every score level needs a value")
    public boolean isEveryLevelValued() {
      return pointValues != null
          && pointValues.keySet().containsAll(EnumSet.allOf(ScoreLevel.class));
    }

    /** The copy a new game keeps. */
    public Settings snapshot() {
      return new Settings(
          duration.toMillis(),
          millis(durationsOffered),
          pointValues,
          leadToWin,
          finalWindow.toMillis(),
          lateScoreWindow.toMillis(),
          chuiLimit,
          roundRobinForfeitPoints,
          millis(addTimeSteps),
          medicalTime.toMillis(),
          timeShownTolerance.toMillis(),
          panelLimit,
          rulebookVersion);
    }

    private static List<Long> millis(List<Duration> durations) {
      return durations.stream().map(Duration::toMillis).toList();
    }
  }
}
