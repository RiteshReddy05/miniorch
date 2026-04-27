package com.miniorch.api.dto;

import com.miniorch.common.PortMapping;
import com.miniorch.persistence.Deployment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        String name,
        String image,
        String tag,
        int desiredReplicas,
        Map<String, String> env,
        List<PortMapping> ports,
        Deployment.Status status,
        Instant createdAt,
        Instant updatedAt,
        List<ReplicaResponse> replicas) {

    public static DeploymentResponse from(Deployment deployment) {
        List<ReplicaResponse> replicaResponses = deployment.getReplicas().stream()
                .sorted((a, b) -> Integer.compare(a.getReplicaIndex(), b.getReplicaIndex()))
                .map(ReplicaResponse::from)
                .toList();
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getName(),
                deployment.getImage(),
                deployment.getTag(),
                deployment.getDesiredReplicas(),
                deployment.getEnv() == null ? Map.of() : Map.copyOf(deployment.getEnv()),
                deployment.getPorts() == null ? List.of() : List.copyOf(deployment.getPorts()),
                deployment.getStatus(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt(),
                replicaResponses);
    }
}
