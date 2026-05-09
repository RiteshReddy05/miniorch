package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.ContainerStatus;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerProberTest {

    @Mock
    private DockerService dockerService;

    @InjectMocks
    private DockerProber prober;

    private final ProbeConfig config = ProbeConfig.dockerDefault();

    @Test
    @DisplayName("supportedType returns DOCKER")
    void supportedType_isDocker() {
        assertThat(prober.supportedType()).isEqualTo(ProbeType.DOCKER);
    }

    @Test
    @DisplayName("PASSING when docker reports state=running")
    void passing_whenRunning() {
        Replica replica = replica("c-0");
        when(dockerService.tryInspect("c-0"))
                .thenReturn(Optional.of(new ContainerStatus("c-0", "running", null, Instant.now())));

        ProbeOutcome outcome = prober.probe(replica, config);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).contains("running");
    }

    @Test
    @DisplayName("FAILING when docker reports a non-running state")
    void failing_whenNotRunning() {
        Replica replica = replica("c-0");
        when(dockerService.tryInspect("c-0"))
                .thenReturn(Optional.of(new ContainerStatus("c-0", "exited", 1, Instant.now())));

        ProbeOutcome outcome = prober.probe(replica, config);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("exited");
    }

    @Test
    @DisplayName("FAILING when container is missing on the host")
    void failing_whenContainerMissing() {
        Replica replica = replica("c-0");
        when(dockerService.tryInspect("c-0")).thenReturn(Optional.empty());

        ProbeOutcome outcome = prober.probe(replica, config);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("missing");
    }

    @Test
    @DisplayName("FAILING when docker inspect throws")
    void failing_whenInspectThrows() {
        Replica replica = replica("c-0");
        when(dockerService.tryInspect("c-0")).thenThrow(new DockerOperationException("daemon down"));

        ProbeOutcome outcome = prober.probe(replica, config);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("daemon down");
    }

    @Test
    @DisplayName("FAILING when replica has no container id")
    void failing_whenNoContainerId() {
        Replica replica = replica(null);

        ProbeOutcome outcome = prober.probe(replica, config);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("no container id");
    }

    private Replica replica(String containerId) {
        return Replica.builder()
                .id(UUID.randomUUID())
                .replicaIndex(0)
                .containerId(containerId)
                .containerName("miniorch-demo-0")
                .status(Replica.Status.RUNNING)
                .build();
    }
}
