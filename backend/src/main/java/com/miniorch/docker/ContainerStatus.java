package com.miniorch.docker;

import java.time.Instant;

public record ContainerStatus(String containerId, String state, Integer exitCode, Instant startedAt) {
}
