package com.management.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class GameConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static final Logger logger = LoggerFactory.getLogger(GameConfig.class);
    private final Properties properties;

    public GameConfig() {
        properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                logger.error("Unable to find config file: {}", CONFIG_FILE);
                return;
            }
            properties.load(inputStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Duration getDefaultDuration() {
        int seconds = Integer.parseInt(properties.getProperty("default.duration"));
        logger.debug("Loaded default time from config file");
        return Duration.ofSeconds(seconds);
    }

    public List<Duration> getOptionalDurations() {
        String propertyValue = properties.getProperty("optional.durations");

        if (propertyValue == null || propertyValue.isBlank()) {
            logger.warn("No optional durations found in the configuration. Using default duration.");
            return List.of(getDefaultDuration());
        }

        try {
            return Arrays.stream(propertyValue.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .map(Duration::ofSeconds)
                    .toList();
        } catch (NumberFormatException e) {
            logger.error("Invalid duration format in configuration: {}. Using default duration.", propertyValue, e);
            return List.of(getDefaultDuration());
        }
    }

}
