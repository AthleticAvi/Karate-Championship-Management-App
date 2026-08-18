package com.management.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The rule values a match is run by, bound from {@code game.*} and validated at startup.
 *
 * <p>Every value here is a rule, not a constant: thresholds and durations change between rule
 * revisions and between championships, and the project forbids any of them appearing in code.
 * Binding them gives typed values, environment overrides and startup validation for free — {@code
 * 120s} in the file arrives as a {@link Duration}, with no hand parsing.
 *
 * @param defaultDuration the match length used when a request names none, or names one outside the
 *     permitted set
 * @param optionalDurations the full set of match lengths a request may choose from
 * @param winningPoints the score a fighter must <em>cross</em> to win; a trigger, never a cap
 * @param foulsEndingMatch the number of fouls that ends a match against the fighter who committed
 *     them
 * @param clockIncrements the time additions a referee may apply to compensate for a late stop
 */
@Validated
@ConfigurationProperties("game")
public record GameProperties(
    @NotNull Duration defaultDuration,
    @NotEmpty List<Duration> optionalDurations,
    @Positive int winningPoints,
    @Positive int foulsEndingMatch,
    @NotEmpty List<Duration> clockIncrements) {

  public GameProperties {
    optionalDurations = List.copyOf(optionalDurations);
    clockIncrements = List.copyOf(clockIncrements);
  }
}
