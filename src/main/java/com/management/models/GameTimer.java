package com.management.models;

import java.time.Duration;
import java.time.LocalDateTime;

public class GameTimer {
    private LocalDateTime startTime;
    private Duration remainingTime;

    public GameTimer(Duration initialDuration) {
        this.remainingTime = initialDuration;
    }

    public void start() {
        this.startTime = LocalDateTime.now();
    }

    public void pause() {
        if (startTime != null) {
            Duration elapsedTime = Duration.between(startTime, LocalDateTime.now());
            this.remainingTime = remainingTime.minus(elapsedTime);
            this.startTime = null;
        }
    }

    public void resume() {
        if (startTime == null) {
            this.startTime = LocalDateTime.now();
        }
    }

    public void stop() {
        this.remainingTime = Duration.ZERO;
        this.startTime = null;
    }

    public Duration getRemainingTime() {
        if (startTime != null) {
            Duration elapsedTime = Duration.between(startTime, LocalDateTime.now());
            return remainingTime.minus(elapsedTime);
        }
        return remainingTime;
    }
}
