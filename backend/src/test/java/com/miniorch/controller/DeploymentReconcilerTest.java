package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.docker.ContainerSpec;
import com.miniorch.docker.ContainerStatus;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.DeploymentEvent;
import com.miniorch.persistence.DeploymentEventRepository;
import com.miniorch.persistence.DeploymentRepository;
import com.miniorch.persistence.Replica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentReconcilerTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentEventRepository eventRepository;

    @Mock
    private DockerService dockerService;

    @Mock
    private BackoffCalculator backoffCalculator;

    @Mock
    private HealthProbeRunner healthProbeRunner;

    @InjectMocks
    private DeploymentReconciler reconciler;

    @BeforeEach
    void defaultProbeOutcome() {
        lenient().when(healthProbeRunner.probe(any(Replica.class), any(ProbeConfig.class)))
                .thenReturn(ProbeOutcome.passing("docker reports running", 1));
    }

    @Test
    @DisplayName("no-op when actual replicas match desired and all are running")
    void noOp_whenActualMatchesDesired() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 3);
        addReplica(deployment, 0, "c-0", Replica.Status.RUNNING);
        addReplica(deployment, 1, "c-1", Replica.Status.RUNNING);
        addReplica(deployment, 2, "c-2", Replica.Status.RUNNING);
        deployment.setLastObservedStatus(DeploymentReconciler.STATUS_HEALTHY);
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect(anyString()))
                .thenReturn(Optional.of(new ContainerStatus("x", "running", null, Instant.now())));

        reconciler.reconcileOne(id);

        verify(dockerService, never()).createAndStart(any(ContainerSpec.class));
        verify(dockerService, never()).stop(anyString(), anyInt());
        verify(dockerService, never()).remove(anyString());
        verify(eventRepository, never()).save(any(DeploymentEvent.class));
    }

    @Test
    @DisplayName("scales up by spawning new replicas at the next sequential indexes")
    void scalesUp_whenActualLessThanDesired() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 3);
        addReplica(deployment, 0, "c-0", Replica.Status.RUNNING);
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect("c-0"))
                .thenReturn(Optional.of(new ContainerStatus("c-0", "running", null, Instant.now())));
        AtomicInteger counter = new AtomicInteger();
        when(dockerService.createAndStart(any(ContainerSpec.class)))
                .thenAnswer(inv -> "new-" + counter.getAndIncrement());

        reconciler.reconcileOne(id);

        verify(dockerService, times(2)).createAndStart(any(ContainerSpec.class));
        List<Replica> spawned = deployment.getReplicas().stream()
                .filter(r -> r.getReplicaIndex() != 0)
                .toList();
        assertThat(spawned).hasSize(2);
        assertThat(spawned).extracting(Replica::getReplicaIndex).containsExactlyInAnyOrder(1, 2);
        assertThat(spawned).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(Replica.Status.RUNNING);
            assertThat(r.getContainerId()).startsWith("new-");
        });
    }

    @Test
    @DisplayName("scales down by removing replicas with the highest indexes")
    void scalesDown_whenActualGreaterThanDesired() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 3);
        for (int i = 0; i < 5; i++) {
            addReplica(deployment, i, "c-" + i, Replica.Status.RUNNING);
        }
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect(anyString()))
                .thenReturn(Optional.of(new ContainerStatus("x", "running", null, Instant.now())));

        reconciler.reconcileOne(id);

        verify(dockerService).stop("c-4", 5);
        verify(dockerService).stop("c-3", 5);
        verify(dockerService).remove("c-4");
        verify(dockerService).remove("c-3");
        verify(dockerService, never()).stop("c-2", 5);
        verify(dockerService, never()).stop("c-1", 5);
        verify(dockerService, never()).stop("c-0", 5);
        verify(dockerService, never()).createAndStart(any(ContainerSpec.class));
        assertThat(replicaAt(deployment, 4).getStatus()).isEqualTo(Replica.Status.REMOVED);
        assertThat(replicaAt(deployment, 3).getStatus()).isEqualTo(Replica.Status.REMOVED);
        assertThat(replicaAt(deployment, 2).getStatus()).isEqualTo(Replica.Status.RUNNING);
    }

    @Test
    @DisplayName("restarts an EXITED replica when backoff has elapsed")
    void restartsExitedReplica_whenBackoffElapsed() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 1);
        Replica replica = addReplica(deployment, 0, "c-old", Replica.Status.EXITED);
        replica.setLastRestartAt(null);
        replica.setRestartCount(0);
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect("c-old"))
                .thenReturn(Optional.of(new ContainerStatus("c-old", "exited", 1, Instant.now())));
        when(dockerService.createAndStart(any(ContainerSpec.class))).thenReturn("c-new");

        reconciler.reconcileOne(id);

        verify(dockerService).stop("c-old", 5);
        verify(dockerService).remove("c-old");
        verify(dockerService).createAndStart(any(ContainerSpec.class));
        assertThat(replica.getContainerId()).isEqualTo("c-new");
        assertThat(replica.getStatus()).isEqualTo(Replica.Status.RUNNING);
        assertThat(replica.getRestartCount()).isEqualTo(1);
        assertThat(replica.getLastRestartAt()).isNotNull();

        ArgumentCaptor<DeploymentEvent> captor = ArgumentCaptor.forClass(DeploymentEvent.class);
        verify(eventRepository, times(3)).save(captor.capture());
        List<DeploymentEvent.Type> types = captor.getAllValues().stream()
                .map(DeploymentEvent::getType)
                .toList();
        assertThat(types).contains(
                DeploymentEvent.Type.REPLICA_RESTART_SCHEDULED,
                DeploymentEvent.Type.REPLICA_RESTART_ATTEMPTED);
    }

    @Test
    @DisplayName("skips restart for an EXITED replica still inside the backoff window")
    void skipsRestart_whenWithinBackoffWindow() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 1);
        Replica replica = addReplica(deployment, 0, "c-0", Replica.Status.EXITED);
        replica.setLastRestartAt(Instant.now());
        replica.setRestartCount(2);
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect("c-0"))
                .thenReturn(Optional.of(new ContainerStatus("c-0", "exited", 1, Instant.now())));
        when(backoffCalculator.nextDelay(2)).thenReturn(Duration.ofSeconds(4));

        reconciler.reconcileOne(id);

        verify(dockerService, never()).createAndStart(any(ContainerSpec.class));
        verify(dockerService, never()).stop(anyString(), anyInt());
        verify(dockerService, never()).remove(anyString());
        assertThat(replica.getRestartCount()).isEqualTo(2);
        assertThat(replica.getStatus()).isEqualTo(Replica.Status.EXITED);
    }

    @Test
    @DisplayName("emits a DEPLOYMENT_DEGRADED event when computed status changes from Healthy")
    void writesTransitionEvent_whenStatusChanges() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeDeployment(id, 2);
        deployment.setLastObservedStatus(DeploymentReconciler.STATUS_HEALTHY);
        Replica running = addReplica(deployment, 0, "c-0", Replica.Status.RUNNING);
        Replica failed = addReplica(deployment, 1, null, Replica.Status.FAILED);
        failed.setLastRestartAt(Instant.now());
        failed.setRestartCount(0);
        when(deploymentRepository.findById(id)).thenReturn(Optional.of(deployment));
        when(dockerService.tryInspect("c-0"))
                .thenReturn(Optional.of(new ContainerStatus("c-0", "running", null, Instant.now())));
        when(backoffCalculator.nextDelay(0)).thenReturn(Duration.ofSeconds(1));

        reconciler.reconcileOne(id);

        verify(dockerService, never()).createAndStart(any(ContainerSpec.class));
        ArgumentCaptor<DeploymentEvent> captor = ArgumentCaptor.forClass(DeploymentEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(DeploymentEvent.Type.DEPLOYMENT_DEGRADED);
        assertThat(deployment.getLastObservedStatus()).isEqualTo(DeploymentReconciler.STATUS_DEGRADED);
        assertThat(running.getStatus()).isEqualTo(Replica.Status.RUNNING);
        assertThat(failed.getStatus()).isEqualTo(Replica.Status.FAILED);
    }

    private Deployment makeDeployment(UUID id, int desired) {
        return Deployment.builder()
                .id(id)
                .name("demo")
                .image("nginx")
                .tag("1.27-alpine")
                .desiredReplicas(desired)
                .env(Map.of())
                .ports(List.of())
                .probe(ProbeConfig.dockerDefault())
                .status(Deployment.Status.RUNNING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .replicas(new ArrayList<>())
                .build();
    }

    private Replica addReplica(Deployment deployment, int index, String containerId, Replica.Status status) {
        Replica replica = Replica.builder()
                .id(UUID.randomUUID())
                .deployment(deployment)
                .replicaIndex(index)
                .containerId(containerId)
                .containerName("miniorch-demo-" + index)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        deployment.getReplicas().add(replica);
        return replica;
    }

    private Replica replicaAt(Deployment deployment, int index) {
        return deployment.getReplicas().stream()
                .filter(r -> r.getReplicaIndex() == index)
                .findFirst()
                .orElseThrow();
    }
}
