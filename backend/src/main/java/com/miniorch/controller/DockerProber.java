package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.ContainerStatus;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DockerProber implements HealthProber {

    private final DockerService dockerService;

    @Override
    public ProbeType supportedType() {
        return ProbeType.DOCKER;
    }

    @Override
    public ProbeOutcome probe(Replica replica, ProbeConfig config) {
        long start = System.nanoTime();
        String containerId = replica.getContainerId();
        if (containerId == null || containerId.isBlank()) {
            return ProbeOutcome.failing("no container id on replica", elapsedMs(start));
        }
        try {
            Optional<ContainerStatus> maybe = dockerService.tryInspect(containerId);
            if (maybe.isEmpty()) {
                return ProbeOutcome.failing("container missing on host", elapsedMs(start));
            }
            String state = maybe.get().state();
            if ("running".equalsIgnoreCase(state)) {
                return ProbeOutcome.passing("docker reports running", elapsedMs(start));
            }
            return ProbeOutcome.failing("docker reports state=" + state, elapsedMs(start));
        } catch (DockerOperationException ex) {
            return ProbeOutcome.failing("inspect failed: " + ex.getMessage(), elapsedMs(start));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
