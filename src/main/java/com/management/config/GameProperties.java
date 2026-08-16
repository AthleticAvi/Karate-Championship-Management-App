package com.management.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Match durations, bound from {@code game.*} in the standard configuration and validated at
 * startup.
 *
 * <p>Replaces the hand-written {@code GameConfig}, which read its own properties file through the
 * classloader — outside the container, so it could not be substituted in a test, overridden per
 * environment, or validated before first use. Binding here gives all three for free, and the values
 * are rich types: {@code 120s} in the file arrives as a {@link Duration}, no hand parsing.
 *
 * @param defaultDuration the match length used when a request names none, or names one outside the
 *     permitted set
 * @param optionalDurations the full set of match lengths a request may choose from
 */
@Validated
@ConfigurationProperties("game")
public record GameProperties(
    @NotNull Duration defaultDuration, @NotEmpty List<Duration> optionalDurations) {

  public GameProperties {
    optionalDurations = List.copyOf(optionalDurations);
  }
}
