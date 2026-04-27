package com.miniorch.service;

import com.miniorch.api.ResourceNotFoundException;
import com.miniorch.api.ValidationException;
import com.miniorch.api.dto.CreateDeploymentRequest;
import com.miniorch.api.dto.DeploymentEventResponse;
import com.miniorch.api.dto.DeploymentResponse;
import com.miniorch.docker.ContainerSpec;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.DeploymentEvent;
import com.miniorch.persistence.DeploymentEventRepository;
import com.miniorch.persistence.DeploymentRepository;
import com.miniorch.persistence.Replica;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private static final int STOP_GRACE_SECONDS = 10;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventRepository eventRepository;
    private final DockerService dockerService;

    @Transactional(noRollbackFor = DockerOperationException.class)
    public DeploymentResponse create(CreateDeploymentRequest request) {
        if (deploymentRepository.existsByName(request.name())) {
            throw new ValidationException("deployment name already exists: " + request.name());
        }

        Deployment deployment = DeploymentMapper.toEntity(request);
        for (int i = 0; i < request.desiredReplicas(); i++) {
            Replica replica = Replica.builder()
                    .deployment(deployment)
                    .replicaIndex(i)
                    .containerName(DeploymentMapper.containerName(deployment, i))
                    .status(Replica.Status.PENDING)
                    .build();
            deployment.getReplicas().add(replica);
        }
        deployment = deploymentRepository.save(deployment);
        recordEvent(deployment, DeploymentEvent.Type.CREATED, "deployment created");

        List<String> startedContainerIds = new ArrayList<>();
        try {
            for (Replica replica : deployment.getReplicas()) {
                ContainerSpec spec = DeploymentMapper.toContainerSpec(deployment, replica);
                String containerId = dockerService.createAndStart(spec);
                startedContainerIds.add(containerId);
                replica.setContainerId(containerId);
                replica.setStatus(Replica.Status.RUNNING);
                recordEvent(deployment, DeploymentEvent.Type.REPLICA_STARTED,
                        "replica " + replica.getReplicaIndex() + " started as " + containerId);
            }
        } catch (DockerOperationException ex) {
            log.error("rolling back deployment {} after docker failure", deployment.getName(), ex);
            for (String containerId : startedContainerIds) {
                try {
                    dockerService.stop(containerId, STOP_GRACE_SECONDS);
                } catch (DockerOperationException stopFailure) {
                    log.warn("rollback stop failed for {}", containerId, stopFailure);
                }
                try {
                    dockerService.remove(containerId);
                } catch (DockerOperationException removeFailure) {
                    log.warn("rollback remove failed for {}", containerId, removeFailure);
                }
            }
            deployment.setStatus(Deployment.Status.FAILED);
            recordEvent(deployment, DeploymentEvent.Type.ERROR, ex.getMessage());
            throw ex;
        }

        deployment.setStatus(Deployment.Status.RUNNING);
        return DeploymentResponse.from(deployment);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse get(UUID id) {
        return DeploymentResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> list() {
        return deploymentRepository.findAll().stream()
                .map(DeploymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeploymentEventResponse> events(UUID id) {
        if (!deploymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("deployment not found: " + id);
        }
        return eventRepository.findByDeploymentIdOrderByCreatedAtDesc(id).stream()
                .map(DeploymentEventResponse::from)
                .toList();
    }

    @Transactional(noRollbackFor = DockerOperationException.class)
    public void delete(UUID id) {
        Deployment deployment = loadOrThrow(id);
        deployment.setStatus(Deployment.Status.DELETING);

        try {
            for (Replica replica : deployment.getReplicas()) {
                String containerId = replica.getContainerId();
                if (containerId != null && !containerId.isBlank()) {
                    dockerService.stop(containerId, STOP_GRACE_SECONDS);
                    dockerService.remove(containerId);
                }
                replica.setStatus(Replica.Status.REMOVED);
            }
        } catch (DockerOperationException ex) {
            log.error("delete failed for deployment {}", deployment.getName(), ex);
            deployment.setStatus(Deployment.Status.FAILED);
            recordEvent(deployment, DeploymentEvent.Type.ERROR, ex.getMessage());
            throw ex;
        }

        eventRepository.deleteAllByDeploymentId(deployment.getId());
        deploymentRepository.delete(deployment);
    }

    private Deployment loadOrThrow(UUID id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("deployment not found: " + id));
    }

    private void recordEvent(Deployment deployment, DeploymentEvent.Type type, String message) {
        DeploymentEvent event = DeploymentEvent.builder()
                .deployment(deployment)
                .type(type)
                .message(message)
                .build();
        eventRepository.save(event);
    }
}
