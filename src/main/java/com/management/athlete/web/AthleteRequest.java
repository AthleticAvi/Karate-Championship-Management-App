package com.management.athlete.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** The body of a create and an update: the athlete's name, and their weight when known. */
public record AthleteRequest(
    @NotBlank(message = "an athlete needs a name") @Size(max = 100) String name,
    @Positive @Nullable Double weightKg) {}
