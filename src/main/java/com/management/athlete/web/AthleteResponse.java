package com.management.athlete.web;

import org.jspecify.annotations.Nullable;

/** An athlete as the API reports them. */
public record AthleteResponse(String id, String name, @Nullable Double weightKg) {}
