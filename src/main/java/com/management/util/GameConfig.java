package com.management.util;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Match durations, loaded and validated once at construction.
 *
 * <p>Every failure path used to degrade into empty state: a missing file, an unreadable file, and a
 * missing or non-numeric {@code default.duration} all let the application start healthy and then
 * fail with a {@code NumberFormatException} the first time a match was created — far from the
 * cause, with a message that never mentioned configuration. Required configuration is validated
 * where it is loaded, so any of those now stops startup with a message naming the file or the key.
 *
 * <p>Interim only: #42 replaces this class with {@code @ConfigurationProperties}, which binds,
 * validates and fails startup on bad configuration for free.
 */
public class GameConfig {
  private static final String CONFIG_FILE = "config.properties";
  private static final String DEFAULT_DURATION_KEY = "default.duration";
  private static final String OPTIONAL_DURATIONS_KEY = "optional.durations";
  private static final Logger log = LoggerFactory.getLogger(GameConfig.class);

  private final Duration defaultDuration;
  private final List<Duration> optionalDurations;

  public GameConfig() {
    Properties properties = loadProperties();
    this.defaultDuration = requiredSeconds(properties);
    this.optionalDurations = parseOptionalDurations(properties);
  }

  public Duration getDefaultDuration() {
    return defaultDuration;
  }

  public List<Duration> getOptionalDurations() {
    return optionalDurations;
  }

  private Properties loadProperties() {
    Properties properties = new Properties();
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
      if (inputStream == null) {
        throw new IllegalStateException(
            "Required configuration file " + CONFIG_FILE + " is not on the classpath.");
      }
      properties.load(inputStream);
    } catch (IOException ex) {
      log.error("Failed to load {}", CONFIG_FILE, ex);
      throw new IllegalStateException(
          "Required configuration file " + CONFIG_FILE + " could not be read.", ex);
    }
    return properties;
  }

  private static Duration requiredSeconds(Properties properties) {
    String raw = properties.getProperty(DEFAULT_DURATION_KEY);
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException(
          "Required key " + DEFAULT_DURATION_KEY + " is missing from " + CONFIG_FILE + ".");
    }
    try {
      return Duration.ofSeconds(Integer.parseInt(raw.trim()));
    } catch (NumberFormatException ex) {
      throw new IllegalStateException(
          "Key "
              + DEFAULT_DURATION_KEY
              + " in "
              + CONFIG_FILE
              + " must be a whole number of"
              + " seconds, but was: "
              + raw,
          ex);
    }
  }

  private List<Duration> parseOptionalDurations(Properties properties) {
    String propertyValue = properties.getProperty(OPTIONAL_DURATIONS_KEY);

    if (propertyValue == null || propertyValue.isBlank()) {
      log.warn("No optional durations found in the configuration. Using default duration.");
      return List.of(defaultDuration);
    }

    try {
      return Arrays.stream(propertyValue.split(","))
          .map(String::trim)
          .map(Integer::parseInt)
          .map(Duration::ofSeconds)
          .toList();
    } catch (NumberFormatException e) {
      log.error(
          "Invalid duration format in configuration: {}. Using default duration.",
          propertyValue,
          e);
      return List.of(defaultDuration);
    }
  }
}
