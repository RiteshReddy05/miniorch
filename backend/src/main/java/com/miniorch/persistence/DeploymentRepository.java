package com.miniorch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Optional<Deployment> findByName(String name);

    boolean existsByName(String name);
}
