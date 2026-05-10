package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpProberTest {

    @Mock
    private DockerService dockerService;

    private HttpProber prober;
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        prober = new HttpProber(dockerService);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/healthy", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/sick", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/notfound", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("supportedType returns HTTP")
    void supportedType_isHttp() {
        assertThat(prober.supportedType()).isEqualTo(ProbeType.HTTP);
    }

    @Test
    @DisplayName("PASSING on 2xx response")
    void passing_on2xx() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));
        ProbeOutcome outcome = prober.probe(replica("c-0"), httpConfig("/healthy", port));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.message()).contains("HTTP 200");
    }

    @Test
    @DisplayName("FAILING on 5xx response")
    void failing_on5xx() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));
        ProbeOutcome outcome = prober.probe(replica("c-0"), httpConfig("/sick", port));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("HTTP 503");
    }

    @Test
    @DisplayName("FAILING on 4xx response")
    void failing_on4xx() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));
        ProbeOutcome outcome = prober.probe(replica("c-0"), httpConfig("/notfound", port));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("HTTP 404");
    }

    @Test
    @DisplayName("FAILING when ip lookup returns empty")
    void failing_whenNoIp() {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.empty());
        ProbeOutcome outcome = prober.probe(replica("c-0"), httpConfig("/healthy", port));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.message()).contains("ip unavailable");
    }

    @Test
    @DisplayName("FAILING when target port is closed")
    void failing_whenPortClosed() throws IOException {
        when(dockerService.getContainerIp("c-0")).thenReturn(Optional.of("127.0.0.1"));
        int closedPort;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }

        ProbeOutcome outcome = prober.probe(replica("c-0"), httpConfig("/healthy", closedPort));

        assertThat(outcome.passed()).isFalse();
    }

    private static ProbeConfig httpConfig(String path, int port) {
        return new ProbeConfig(ProbeType.HTTP, path, port, 10, 2, 3);
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
