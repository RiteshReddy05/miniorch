package com.miniorch.controller;

import com.miniorch.persistence.Replica;

public record ProbeOutcome(Replica.ProbeResult result, String message, long durationMs) {

    public boolean passed() {
        return result == Replica.ProbeResult.PASSING;
    }

    public static ProbeOutcome passing(String message, long durationMs) {
        return new ProbeOutcome(Replica.ProbeResult.PASSING, message, durationMs);
    }

    public static ProbeOutcome failing(String message, long durationMs) {
        return new ProbeOutcome(Replica.ProbeResult.FAILING, message, durationMs);
    }
}
