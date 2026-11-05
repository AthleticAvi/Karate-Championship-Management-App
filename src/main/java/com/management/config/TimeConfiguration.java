package com.management.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of the current time.
 *
 * <p>UTC, so no instance's zone or daylight saving reaches a stored time. Whole milliseconds,
 * because that is what a BSON date keeps: a finer clock would give a value in memory that differs
 * from the one a reload returns.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

  @Bean
  Clock clock() {
    return Clock.tickMillis(ZoneOffset.UTC);
  }
}
