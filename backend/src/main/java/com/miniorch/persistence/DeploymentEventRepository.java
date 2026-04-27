package com.miniorch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, UUID> {

    List<DeploymentEvent> findByDeploymentIdOrderByCreatedAtDesc(UUID deploymentId);

    @Modifying
    @Query("delete from DeploymentEvent e where e.deployment.id = :deploymentId")
    void deleteAllByDeploymentId(@Param("deploymentId") UUID deploymentId);
}
