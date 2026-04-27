package com.miniorch.service;

import com.miniorch.api.dto.CreateDeploymentRequest;
import com.miniorch.docker.ContainerSpec;
import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.Replica;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DeploymentMapper {

    private DeploymentMapper() {}

    public static Deployment toEntity(CreateDeploymentRequest request) {
        return Deployment.builder()
                .name(request.name())
                .image(request.image())
                .tag(request.tag())
                .desiredReplicas(request.desiredReplicas())
                .env(new HashMap<>(request.envOrEmpty()))
                .ports(new java.util.ArrayList<>(request.portsOrEmpty()))
                .status(Deployment.Status.PENDING)
                .build();
    }

    public static String containerName(Deployment deployment, int replicaIndex) {
        return "miniorch-" + deployment.getName() + "-" + replicaIndex;
    }

    public static ContainerSpec toContainerSpec(Deployment deployment, Replica replica) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("miniorch.managed", "true");
        labels.put("miniorch.deployment-id", deployment.getId().toString());
        labels.put("miniorch.deployment-name", deployment.getName());
        labels.put("miniorch.replica-index", Integer.toString(replica.getReplicaIndex()));

        return new ContainerSpec(
                deployment.getImage(),
                deployment.getTag(),
                replica.getContainerName(),
                deployment.getEnv() == null ? Map.of() : deployment.getEnv(),
                deployment.getPorts() == null ? java.util.List.of() : deployment.getPorts(),
                labels);
    }
}
