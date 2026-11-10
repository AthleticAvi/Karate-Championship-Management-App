package com.management.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Created and modified instants on stored documents, from Spring Data auditing.
 *
 * <p>The time comes from the one {@link Clock} bean, never the system clock, so a test that fixes
 * the clock fixes these too. A class of its own and not an annotation on the application class,
 * because a web slice would then start auditing with no datastore behind it.
 */
@Configuration(proxyBeanMethods = false)
@EnableMongoAuditing(dateTimeProviderRef = "auditingTime")
public class AuditingConfiguration {

  @Bean
  DateTimeProvider auditingTime(Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
