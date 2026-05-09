package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpProberTest {

    @Mock
    private DockerService dockerService;

    private TcpProber prober;
    private ServerSocket server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        prober = new TcpProber(dockerService);
        server = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
        port = server.getLocalPort();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (!server.isClosed()) {
            server.close();
        }
    }

    @Test
    @DisplayName("supportedType returns TCP")
    void supportedType_isTcp() {
        assertThat(prober.supportedType()).isEqualTo(ProbeType.TCP);
    }

    @Test
    @DisplayName("PASSING when TCP connect succeeds")
    void passing_whenConnectSucceeds() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));

        ProbeOutcome outcome = prober.probe(replica("c-0"), tcpConfig(port));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).contains(Integer.toString(port));
    }

    @Test
    @DisplayName("FAILING when target port is closed")
    void failing_whenPortClosed() throws IOException {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));
        int closedPort;
        try (ServerSocket s = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            closedPort = s.getLocalPort();
        }

        ProbeOutcome outcome = prober.probe(replica("c-0"), tcpConfig(closedPort));

        assertThat(outcome.passed()).isFalse();
    }

    @Test
    @DisplayName("FAILING when ip lookup returns empty")
    void failing_whenNoIp() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.empty());

        ProbeOutcome outcome = prober.probe(replica("c-0"), tcpConfig(port));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("ip unavailable");
    }

    private static ProbeConfig tcpConfig(int port) {
        return new ProbeConfig(ProbeType.TCP, null, port, 10, 2, 3);
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
