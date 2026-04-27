package com.miniorch.service;

import com.miniorch.api.ValidationException;
import com.miniorch.api.dto.CreateDeploymentRequest;
import com.miniorch.api.dto.DeploymentResponse;
import com.miniorch.common.PortMapping;
import com.miniorch.docker.ContainerSpec;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.DeploymentEvent;
import com.miniorch.persistence.DeploymentEventRepository;
import com.miniorch.persistence.DeploymentRepository;
import com.miniorch.persistence.Replica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentEventRepository eventRepository;

    @Mock
    private DockerService dockerService;

    @InjectMocks
    private DeploymentService deploymentService;

    private CreateDeploymentRequest requestWith(int replicas) {
        return new CreateDeploymentRequest(
                "demo",
                "nginx",
                "1.27-alpine",
                replicas,
                Map.of(),
                List.of(new PortMapping(18080, 80, "tcp")));
    }

    private void stubSaveEchosDeployment() {
        when(deploymentRepository.save(any(Deployment.class)))
                .thenAnswer(inv -> {
                    Deployment d = inv.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID());
                    }
                    return d;
                });
    }

    @Test
    @DisplayName("create persists deployment, starts containers, writes CREATED + REPLICA_STARTED events")
    void create_persistsDeploymentAndStartsContainers() {
        when(deploymentRepository.existsByName("demo")).thenReturn(false);
        stubSaveEchosDeployment();
        AtomicInteger counter = new AtomicInteger();
        when(dockerService.createAndStart(any(ContainerSpec.class)))
                .thenAnswer(inv -> "container-" + counter.getAndIncrement());

        DeploymentResponse response = deploymentService.create(requestWith(2));

        assertThat(response.status()).isEqualTo(Deployment.Status.RUNNING);
        assertThat(response.replicas()).hasSize(2);
        assertThat(response.replicas())
                .allSatisfy(r -> assertThat(r.status()).isEqualTo(Replica.Status.RUNNING));
        assertThat(response.replicas().get(0).containerId()).isEqualTo("container-0");
        assertThat(response.replicas().get(1).containerId()).isEqualTo("container-1");

        verify(dockerService, times(2)).createAndStart(any(ContainerSpec.class));

        ArgumentCaptor<DeploymentEvent> eventCaptor = ArgumentCaptor.forClass(DeploymentEvent.class);
        verify(eventRepository, times(3)).save(eventCaptor.capture());
        List<DeploymentEvent.Type> types = eventCaptor.getAllValues().stream()
                .map(DeploymentEvent::getType)
                .toList();
        assertThat(types).containsExactly(
                DeploymentEvent.Type.CREATED,
                DeploymentEvent.Type.REPLICA_STARTED,
                DeploymentEvent.Type.REPLICA_STARTED);
    }

    @Test
    @DisplayName("create rejects duplicate name without touching docker")
    void create_rejectsDuplicateName() {
        when(deploymentRepository.existsByName("demo")).thenReturn(true);

        assertThatThrownBy(() -> deploymentService.create(requestWith(1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("demo");

        verify(dockerService, never()).createAndStart(any());
        verify(deploymentRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rolls back started containers when a later replica fails to start")
    void create_rollsBackOnDockerFailure() {
        when(deploymentRepository.existsByName("demo")).thenReturn(false);
        stubSaveEchosDeployment();
        when(dockerService.createAndStart(any(ContainerSpec.class)))
                .thenReturn("container-0")
                .thenThrow(new DockerOperationException("port collision on second replica"));

        assertThatThrownBy(() -> deploymentService.create(requestWith(2)))
                .isInstanceOf(DockerOperationException.class)
                .hasMessageContaining("port collision");

        verify(dockerService, times(2)).createAndStart(any(ContainerSpec.class));
        verify(dockerService).stop(eq("container-0"), anyInt());
        verify(dockerService).remove("container-0");

        ArgumentCaptor<DeploymentEvent> eventCaptor = ArgumentCaptor.forClass(DeploymentEvent.class);
        verify(eventRepository, atLeastOnce()).save(eventCaptor.capture());
        List<DeploymentEvent.Type> types = eventCaptor.getAllValues().stream()
                .map(DeploymentEvent::getType)
                .toList();
        assertThat(types).contains(DeploymentEvent.Type.ERROR);

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(deploymentCaptor.capture());
        assertThat(deploymentCaptor.getValue().getStatus()).isEqualTo(Deployment.Status.FAILED);
    }

    @Test
    @DisplayName("delete stops + removes every replica then deletes the deployment row")
    void delete_stopsAndRemovesAllReplicas() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeRunningDeployment(id, List.of("c-0", "c-1"));
        when(deploymentRepository.findById(id)).thenReturn(java.util.Optional.of(deployment));

        deploymentService.delete(id);

        verify(dockerService).stop(eq("c-0"), anyInt());
        verify(dockerService).remove("c-0");
        verify(dockerService).stop(eq("c-1"), anyInt());
        verify(dockerService).remove("c-1");
        verify(eventRepository).deleteAllByDeploymentId(id);
        verify(deploymentRepository).delete(deployment);
        assertThat(deployment.getReplicas())
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(Replica.Status.REMOVED));
    }

    @Test
    @DisplayName("delete keeps the row in FAILED status when docker fails mid-loop")
    void delete_keepsRowOnDockerFailure() {
        UUID id = UUID.randomUUID();
        Deployment deployment = makeRunningDeployment(id, List.of("c-0", "c-1"));
        when(deploymentRepository.findById(id)).thenReturn(java.util.Optional.of(deployment));
        org.mockito.Mockito.doNothing().when(dockerService).stop(eq("c-0"), anyInt());
        org.mockito.Mockito.doNothing().when(dockerService).remove("c-0");
        org.mockito.Mockito.doThrow(new DockerOperationException("daemon unreachable"))
                .when(dockerService).stop(eq("c-1"), anyInt());

        assertThatThrownBy(() -> deploymentService.delete(id))
                .isInstanceOf(DockerOperationException.class)
                .hasMessageContaining("daemon unreachable");

        verify(deploymentRepository, never()).delete(any(Deployment.class));
        verify(eventRepository, never()).deleteAllByDeploymentId(any());
        assertThat(deployment.getStatus()).isEqualTo(Deployment.Status.FAILED);

        ArgumentCaptor<DeploymentEvent> eventCaptor = ArgumentCaptor.forClass(DeploymentEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(DeploymentEvent.Type.ERROR);
        assertThat(eventCaptor.getValue().getMessage()).contains("daemon unreachable");
    }

    private Deployment makeRunningDeployment(UUID id, List<String> containerIds) {
        Deployment deployment = Deployment.builder()
                .id(id)
                .name("demo")
                .image("nginx")
                .tag("1.27-alpine")
                .desiredReplicas(containerIds.size())
                .env(Map.of())
                .ports(List.of())
                .status(Deployment.Status.RUNNING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .replicas(new ArrayList<>())
                .build();
        for (int i = 0; i < containerIds.size(); i++) {
            deployment.getReplicas().add(Replica.builder()
                    .id(UUID.randomUUID())
                    .deployment(deployment)
                    .replicaIndex(i)
                    .containerId(containerIds.get(i))
                    .containerName("miniorch-demo-" + i)
                    .status(Replica.Status.RUNNING)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }
        return deployment;
    }
}
