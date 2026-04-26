package com.miniorch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, UUID> {

    List<DeploymentEvent> findByDeploymentIdOrderByCreatedAtDesc(UUID deploymentId);
}
