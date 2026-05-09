package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.docker.ContainerSpec;
import com.miniorch.docker.ContainerStatus;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.DeploymentEvent;
import com.miniorch.persistence.DeploymentEventRepository;
import com.miniorch.persistence.DeploymentRepository;
import com.miniorch.persistence.Replica;
import com.miniorch.service.DeploymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentReconciler {

    static final String STATUS_HEALTHY = "Healthy";
    static final String STATUS_PENDING = "Pending";
    static final String STATUS_DEGRADED = "Degraded";
    static final String STATUS_FAILED = "Failed";

    private static final int STOP_GRACE_SECONDS = 5;
    private static final int CRASHLOOP_THRESHOLD = 5;
    private static final Duration CRASHLOOP_WINDOW = Duration.ofMinutes(5);
    private static final int FAILURE_WINDOW_CAP = 50;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventRepository eventRepository;
    private final DockerService dockerService;
    private final BackoffCalculator backoffCalculator;
    private final HealthProbeRunner healthProbeRunner;

    @Transactional
    public void reconcileOne(UUID deploymentId) {
        Optional<Deployment> maybe = deploymentRepository.findById(deploymentId);
        if (maybe.isEmpty()) {
            return;
        }
        Deployment deployment = maybe.get();
        if (deployment.getStatus() == Deployment.Status.DELETING) {
            return;
        }

        observe(deployment);
        runProbes(deployment);
        attemptRestarts(deployment);
        converge(deployment);
        emitTransitionIfChanged(deployment);
    }

    private void observe(Deployment deployment) {
        Instant now = Instant.now();
        for (Replica replica : deployment.getReplicas()) {
            if (replica.getStatus() == Replica.Status.REMOVED
                    || replica.getStatus() == Replica.Status.CRASHLOOP_BACKOFF) {
                continue;
            }
            String containerId = replica.getContainerId();
            if (containerId == null || containerId.isBlank()) {
                continue;
            }
            Replica.Status prev = replica.getStatus();
            Optional<ContainerStatus> maybe = dockerService.tryInspect(containerId);
            replica.setLastInspectedAt(now);
            if (maybe.isEmpty()) {
                replica.setStatus(Replica.Status.EXITED);
                replica.setLastError("container missing on host");
                if (isFreshFailure(prev)) {
                    onFailure(deployment, replica, now, "container missing on host");
                }
                continue;
            }
            Replica.Status mapped = mapDockerState(maybe.get());
            if (mapped != null) {
                replica.setStatus(mapped);
                if (mapped == Replica.Status.RUNNING) {
                    replica.setLastError(null);
                } else if (mapped == Replica.Status.EXITED) {
                    Integer code = maybe.get().exitCode();
                    String detail = "container exited" + (code == null ? "" : " (exitCode=" + code + ")");
                    replica.setLastError(detail);
                    if (isFreshFailure(prev)) {
                        onFailure(deployment, replica, now, detail);
                    }
                }
            }
        }
    }

    private static boolean isFreshFailure(Replica.Status prev) {
        return prev == Replica.Status.RUNNING || prev == Replica.Status.PENDING;
    }

    private void runProbes(Deployment deployment) {
        ProbeConfig config = deployment.getProbe();
        if (config == null) {
            return;
        }
        Instant now = Instant.now();
        Duration interval = Duration.ofSeconds(config.intervalSeconds());
        for (Replica replica : deployment.getReplicas()) {
            if (replica.getStatus() != Replica.Status.RUNNING) {
                continue;
            }
            Instant last = replica.getLastProbeAt();
            if (last != null && now.isBefore(last.plus(interval))) {
                continue;
            }
            ProbeOutcome outcome = healthProbeRunner.probe(replica, config);
            replica.setLastProbeAt(now);
            replica.setProbeDetails(outcome.message());
            Replica.ProbeResult prev = replica.getLastProbeResult();
            if (outcome.passed()) {
                replica.setConsecutiveFailures(0);
                if (prev == Replica.ProbeResult.FAILING) {
                    recordEvent(deployment, DeploymentEvent.Type.HEALTH_CHECK_PASSED,
                            "replica " + replica.getReplicaIndex() + " probe recovered: " + outcome.message());
                }
                replica.setLastProbeResult(Replica.ProbeResult.PASSING);
            } else {
                int failures = replica.getConsecutiveFailures() + 1;
                replica.setConsecutiveFailures(failures);
                if (failures >= config.failureThreshold() && prev != Replica.ProbeResult.FAILING) {
                    recordEvent(deployment, DeploymentEvent.Type.HEALTH_CHECK_FAILED,
                            "replica " + replica.getReplicaIndex() + " probe failed (" + failures + " consecutive): "
                                    + outcome.message());
                    replica.setLastProbeResult(Replica.ProbeResult.FAILING);
                    onFailure(deployment, replica, now, "probe failed: " + outcome.message());
                }
            }
        }
    }

    private void attemptRestarts(Deployment deployment) {
        Instant now = Instant.now();
        for (Replica replica : deployment.getReplicas()) {
            Replica.Status status = replica.getStatus();
            if (status != Replica.Status.EXITED && status != Replica.Status.FAILED) {
                continue;
            }
            if (!gateAllows(replica, now)) {
                continue;
            }
            recordEvent(deployment, DeploymentEvent.Type.REPLICA_RESTART_SCHEDULED,
                    "replica " + replica.getReplicaIndex() + " restart scheduled (attempt "
                            + (replica.getRestartCount() + 1) + ", was " + status + ")");
            try {
                String oldContainerId = replica.getContainerId();
                if (oldContainerId != null && !oldContainerId.isBlank()) {
                    dockerService.stop(oldContainerId, STOP_GRACE_SECONDS);
                    dockerService.remove(oldContainerId);
                }
                ContainerSpec spec = DeploymentMapper.toContainerSpec(deployment, replica);
                String newContainerId = dockerService.createAndStart(spec);
                replica.setContainerId(newContainerId);
                replica.setStatus(Replica.Status.RUNNING);
                replica.setLastError(null);
                replica.setRestartCount(replica.getRestartCount() + 1);
                replica.setLastRestartAt(Instant.now());
                recordEvent(deployment, DeploymentEvent.Type.REPLICA_RESTART_ATTEMPTED,
                        "replica " + replica.getReplicaIndex() + " restarted as " + newContainerId);
                log.info("reconciler restarted deployment={} replicaIndex={} container={}",
                        deployment.getName(), replica.getReplicaIndex(), newContainerId);
            } catch (DockerOperationException ex) {
                Instant failedAt = Instant.now();
                replica.setStatus(Replica.Status.FAILED);
                replica.setLastError(ex.getMessage());
                replica.setRestartCount(replica.getRestartCount() + 1);
                replica.setLastRestartAt(failedAt);
                recordEvent(deployment, DeploymentEvent.Type.ERROR,
                        "replica " + replica.getReplicaIndex() + " restart failed: " + ex.getMessage());
                log.warn("reconciler restart failed deployment={} replicaIndex={}",
                        deployment.getName(), replica.getReplicaIndex(), ex);
                onFailure(deployment, replica, failedAt, "restart failed: " + ex.getMessage());
            }
        }
    }

    private void onFailure(Deployment deployment, Replica replica, Instant now, String reason) {
        if (replica.getStatus() == Replica.Status.CRASHLOOP_BACKOFF) {
            return;
        }
        List<Instant> window = recordFailure(replica, now);
        if (window.size() >= CRASHLOOP_THRESHOLD) {
            replica.setStatus(Replica.Status.CRASHLOOP_BACKOFF);
            recordEvent(deployment, DeploymentEvent.Type.CRASHLOOP_BACKOFF_TRIPPED,
                    "replica " + replica.getReplicaIndex() + " entered CrashLoopBackOff after "
                            + window.size() + " failures in 5min: " + reason);
            log.warn("reconciler crashloop deployment={} replicaIndex={} reason={}",
                    deployment.getName(), replica.getReplicaIndex(), reason);
        }
    }

    private List<Instant> recordFailure(Replica replica, Instant now) {
        List<Instant> existing = replica.getFailureWindow();
        List<Instant> window = new ArrayList<>(existing == null ? List.of() : existing);
        window.add(now);
        Instant cutoff = now.minus(CRASHLOOP_WINDOW);
        window.removeIf(ts -> ts.isBefore(cutoff));
        while (window.size() > FAILURE_WINDOW_CAP) {
            window.remove(0);
        }
        replica.setFailureWindow(window);
        return window;
    }

    private boolean gateAllows(Replica replica, Instant now) {
        Instant last = replica.getLastRestartAt();
        if (last == null) {
            return true;
        }
        Duration backoff = backoffCalculator.nextDelay(replica.getRestartCount());
        return !now.isBefore(last.plus(backoff));
    }

    private void converge(Deployment deployment) {
        List<Replica> active = deployment.getReplicas().stream()
                .filter(r -> r.getStatus() != Replica.Status.REMOVED)
                .toList();
        int actual = active.size();
        int desired = deployment.getDesiredReplicas();

        if (actual < desired) {
            int nextIndex = nextReplicaIndex(deployment);
            int toStart = desired - actual;
            for (int i = 0; i < toStart; i++) {
                spawnReplica(deployment, nextIndex + i);
            }
        } else if (actual > desired) {
            int toRemove = actual - desired;
            List<Replica> victims = active.stream()
                    .sorted(Comparator.comparingInt(Replica::getReplicaIndex).reversed())
                    .limit(toRemove)
                    .toList();
            for (Replica victim : victims) {
                removeReplica(deployment, victim);
            }
        }
    }

    private void spawnReplica(Deployment deployment, int index) {
        Replica replica = Replica.builder()
                .deployment(deployment)
                .replicaIndex(index)
                .containerName(DeploymentMapper.containerName(deployment, index))
                .status(Replica.Status.PENDING)
                .build();
        deployment.getReplicas().add(replica);
        try {
            ContainerSpec spec = DeploymentMapper.toContainerSpec(deployment, replica);
            String containerId = dockerService.createAndStart(spec);
            replica.setContainerId(containerId);
            replica.setStatus(Replica.Status.RUNNING);
            recordEvent(deployment, DeploymentEvent.Type.REPLICA_STARTED,
                    "replica " + index + " started as " + containerId);
            log.info("reconciler spawned deployment={} replicaIndex={} container={}",
                    deployment.getName(), index, containerId);
        } catch (DockerOperationException ex) {
            replica.setStatus(Replica.Status.FAILED);
            replica.setLastError(ex.getMessage());
            recordEvent(deployment, DeploymentEvent.Type.ERROR,
                    "replica " + index + " failed to start: " + ex.getMessage());
            log.warn("reconciler spawn failed deployment={} replicaIndex={}",
                    deployment.getName(), index, ex);
        }
    }

    private void removeReplica(Deployment deployment, Replica replica) {
        try {
            String containerId = replica.getContainerId();
            if (containerId != null && !containerId.isBlank()) {
                dockerService.stop(containerId, STOP_GRACE_SECONDS);
                dockerService.remove(containerId);
            }
            replica.setStatus(Replica.Status.REMOVED);
            recordEvent(deployment, DeploymentEvent.Type.REPLICA_REMOVED,
                    "replica " + replica.getReplicaIndex() + " removed (scale down)");
            log.info("reconciler removed deployment={} replicaIndex={}",
                    deployment.getName(), replica.getReplicaIndex());
        } catch (DockerOperationException ex) {
            replica.setLastError(ex.getMessage());
            recordEvent(deployment, DeploymentEvent.Type.ERROR,
                    "replica " + replica.getReplicaIndex() + " removal failed: " + ex.getMessage());
            log.warn("reconciler remove failed deployment={} replicaIndex={}",
                    deployment.getName(), replica.getReplicaIndex(), ex);
        }
    }

    private void emitTransitionIfChanged(Deployment deployment) {
        String currentStatus = computeStatus(deployment);
        String previous = deployment.getLastObservedStatus();
        if (currentStatus.equals(previous)) {
            return;
        }
        deployment.setLastObservedStatus(currentStatus);
        switch (currentStatus) {
            case STATUS_HEALTHY -> recordEvent(deployment, DeploymentEvent.Type.DEPLOYMENT_HEALTHY,
                    "deployment healthy");
            case STATUS_DEGRADED -> recordEvent(deployment, DeploymentEvent.Type.DEPLOYMENT_DEGRADED,
                    "deployment degraded");
            case STATUS_FAILED -> recordEvent(deployment, DeploymentEvent.Type.ERROR,
                    "deployment failed (all replicas failed)");
            default -> {
            }
        }
    }

    private String computeStatus(Deployment deployment) {
        List<Replica> active = deployment.getReplicas().stream()
                .filter(r -> r.getStatus() != Replica.Status.REMOVED)
                .toList();
        if (active.isEmpty()) {
            return STATUS_PENDING;
        }
        boolean anyDown = active.stream().anyMatch(DeploymentReconciler::isDown);
        boolean anyRunning = active.stream().anyMatch(r -> r.getStatus() == Replica.Status.RUNNING);
        boolean anyPending = active.stream().anyMatch(r -> r.getStatus() == Replica.Status.PENDING);
        boolean allRunning = active.stream().allMatch(r -> r.getStatus() == Replica.Status.RUNNING);
        boolean allDown = active.stream().allMatch(DeploymentReconciler::isDown);

        if (allRunning) return STATUS_HEALTHY;
        if (allDown) return STATUS_FAILED;
        if (anyDown && anyRunning) return STATUS_DEGRADED;
        if (anyPending) return STATUS_PENDING;
        return STATUS_PENDING;
    }

    private static boolean isDown(Replica replica) {
        Replica.Status s = replica.getStatus();
        return s == Replica.Status.FAILED || s == Replica.Status.CRASHLOOP_BACKOFF;
    }

    private int nextReplicaIndex(Deployment deployment) {
        return deployment.getReplicas().stream()
                .mapToInt(Replica::getReplicaIndex)
                .max()
                .orElse(-1) + 1;
    }

    private Replica.Status mapDockerState(ContainerStatus status) {
        String state = status.state();
        if (state == null) {
            return null;
        }
        return switch (state.toLowerCase()) {
            case "running" -> Replica.Status.RUNNING;
            case "exited", "dead" -> Replica.Status.EXITED;
            case "created", "restarting", "paused" -> null;
            default -> null;
        };
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
