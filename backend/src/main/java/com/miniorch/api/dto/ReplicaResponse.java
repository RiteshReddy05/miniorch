package com.miniorch.api.dto;

import com.miniorch.persistence.Replica;

import java.util.UUID;

public record ReplicaResponse(
        UUID id,
        int replicaIndex,
        String containerId,
        String containerName,
        Replica.Status status,
        String lastError) {

    public static ReplicaResponse from(Replica replica) {
        return new ReplicaResponse(
                replica.getId(),
                replica.getReplicaIndex(),
                replica.getContainerId(),
                replica.getContainerName(),
                replica.getStatus(),
                replica.getLastError());
    }
}
