package com.management.models;

/** Time to add to the clock, as the business layer sees it. The gateway maps the request. */
public record NewClockAdjustment(int seconds, String addedBy) {}
