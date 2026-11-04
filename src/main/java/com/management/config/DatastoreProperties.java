package com.management.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The bounds on talking to the datastore, bound from {@code datastore.*} and validated at startup.
 *
 * <p>Both must be positive: the driver reads a zero operation timeout as "wait forever", which is
 * the unbounded wait this setting exists to remove.
 *
 * @param operationTimeout the longest one datastore operation may take, retries included
 * @param startupWait how long startup waits for the datastore to answer before treating it as
 *     unreachable and starting anyway
 */
@Validated
@ConfigurationProperties("datastore")
public record DatastoreProperties(
    @NotNull @DurationMin(millis = 1) Duration operationTimeout,
    @NotNull @DurationMin(millis = 1) Duration startupWait) {}
