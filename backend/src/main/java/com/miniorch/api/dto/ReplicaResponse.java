package com.miniorch.api.dto;

import com.miniorch.persistence.Replica;

import java.time.Instant;
import java.util.UUID;

public record ReplicaResponse(
        UUID id,
        int replicaIndex,
        String containerId,
        String containerName,
        Replica.Status status,
        String lastError,
        int restartCount,
        Replica.ProbeResult lastProbeResult,
        Instant lastProbeAt,
        int consecutiveFailures,
        String probeDetails) {

    public static ReplicaResponse from(Replica replica) {
        return new ReplicaResponse(
                replica.getId(),
                replica.getReplicaIndex(),
                replica.getContainerId(),
                replica.getContainerName(),
                replica.getStatus(),
                replica.getLastError(),
                replica.getRestartCount(),
                replica.getLastProbeResult(),
                replica.getLastProbeAt(),
                replica.getConsecutiveFailures(),
                replica.getProbeDetails());
    }
}
