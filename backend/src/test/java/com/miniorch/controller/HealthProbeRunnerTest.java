package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.persistence.Replica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthProbeRunnerTest {

    @Test
    @DisplayName("dispatches by ProbeType to the correct prober")
    void dispatches_byProbeType() {
        StubProber http = new StubProber(ProbeType.HTTP, "http-result");
        StubProber tcp = new StubProber(ProbeType.TCP, "tcp-result");
        StubProber docker = new StubProber(ProbeType.DOCKER, "docker-result");
        HealthProbeRunner runner = new HealthProbeRunner(List.of(http, tcp, docker));

        Replica replica = newReplica();
        ProbeOutcome httpOutcome = runner.probe(replica, new ProbeConfig(ProbeType.HTTP, "/", 80, 10, 2, 3));
        ProbeOutcome tcpOutcome = runner.probe(replica, new ProbeConfig(ProbeType.TCP, null, 80, 10, 2, 3));
        ProbeOutcome dockerOutcome = runner.probe(replica, ProbeConfig.dockerDefault());

        assertThat(httpOutcome.message()).isEqualTo("http-result");
        assertThat(tcpOutcome.message()).isEqualTo("tcp-result");
        assertThat(dockerOutcome.message()).isEqualTo("docker-result");
    }

    @Test
    @DisplayName("throws when no prober is registered for the requested type")
    void throws_whenNoProber() {
        HealthProbeRunner runner = new HealthProbeRunner(List.of(new StubProber(ProbeType.DOCKER, "ok")));

        assertThatThrownBy(() -> runner.probe(newReplica(), new ProbeConfig(ProbeType.HTTP, "/", 80, 10, 2, 3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP");
    }

    private static Replica newReplica() {
        return Replica.builder()
                .id(UUID.randomUUID())
                .replicaIndex(0)
                .containerId("c-0")
                .containerName("miniorch-demo-0")
                .status(Replica.Status.RUNNING)
                .build();
    }

    private record StubProber(ProbeType type, String message) implements HealthProber {
        @Override
        public ProbeType supportedType() {
            return type;
        }

        @Override
        public ProbeOutcome probe(Replica replica, ProbeConfig config) {
            return ProbeOutcome.passing(message, 0);
        }
    }
}
