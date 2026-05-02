package com.miniorch.controller;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BackoffCalculator {

    private static final long CAP_SECONDS = 30;

    public Duration nextDelay(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0: " + attempt);
        }
        long seconds = Math.min((long) Math.pow(2, attempt), CAP_SECONDS);
        return Duration.ofSeconds(seconds);
    }
}
