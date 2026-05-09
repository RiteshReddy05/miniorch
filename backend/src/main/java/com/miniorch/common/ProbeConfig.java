package com.miniorch.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProbeConfig(
        @NotNull ProbeType type,
        String path,
        Integer port,
        @Min(5) @Max(300) int intervalSeconds,
        @Min(1) @Max(60) int timeoutSeconds,
        @Min(1) @Max(10) int failureThreshold) {

    public ProbeConfig {
        if (type == null) {
            throw new IllegalArgumentException("probe type is required");
        }
        if (type == ProbeType.HTTP) {
            if (path == null || path.isBlank()) {
                path = "/";
            } else if (!path.startsWith("/")) {
                throw new IllegalArgumentException("HTTP probe path must start with /: " + path);
            }
            if (port == null || port < 1 || port > 65535) {
                throw new IllegalArgumentException("HTTP probe requires port in 1..65535: " + port);
            }
        } else if (type == ProbeType.TCP) {
            path = null;
            if (port == null || port < 1 || port > 65535) {
                throw new IllegalArgumentException("TCP probe requires port in 1..65535: " + port);
            }
        } else {
            path = null;
            port = null;
        }
        if (intervalSeconds < 5) {
            throw new IllegalArgumentException("intervalSeconds must be >= 5: " + intervalSeconds);
        }
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 1: " + timeoutSeconds);
        }
        if (timeoutSeconds >= intervalSeconds) {
            throw new IllegalArgumentException(
                    "timeoutSeconds (" + timeoutSeconds + ") must be < intervalSeconds (" + intervalSeconds + ")");
        }
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1: " + failureThreshold);
        }
    }

    public static ProbeConfig dockerDefault() {
        return new ProbeConfig(ProbeType.DOCKER, null, null, 10, 2, 3);
    }
}
