package com.management.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The bounds on talking to the datastore, bound from {@code datastore.*} and validated at startup.
 *
 * <p>All must be positive: the driver reads a zero timeout as "wait forever", which is the
 * unbounded wait these settings exist to remove.
 *
 * @param operationTimeout the longest one datastore operation may take, retries included
 * @param connectTimeout how long opening one connection may take
 * @param serverSelectionTimeout how long an operation may wait for a usable server
 * @param startupWait how long startup waits for the datastore to answer before treating it as
 *     unreachable and starting anyway
 */
@Validated
@ConfigurationProperties("datastore")
public record DatastoreProperties(
    @NotNull @DurationMin(millis = 1) Duration operationTimeout,
    @NotNull @DurationMin(millis = 1) Duration connectTimeout,
    @NotNull @DurationMin(millis = 1) Duration serverSelectionTimeout,
    @NotNull @DurationMin(millis = 1) Duration startupWait) {}
