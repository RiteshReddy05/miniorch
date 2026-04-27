package com.miniorch.api.dto;

import com.miniorch.persistence.DeploymentEvent;

import java.time.Instant;
import java.util.UUID;

public record DeploymentEventResponse(
        UUID id,
        DeploymentEvent.Type type,
        String message,
        Instant createdAt) {

    public static DeploymentEventResponse from(DeploymentEvent event) {
        return new DeploymentEventResponse(
                event.getId(),
                event.getType(),
                event.getMessage(),
                event.getCreatedAt());
    }
}
