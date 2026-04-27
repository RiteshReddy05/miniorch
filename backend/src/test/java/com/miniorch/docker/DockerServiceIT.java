package com.miniorch.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.miniorch.common.PortMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("docker")
class DockerServiceIT {

    private static final String BUSYBOX = "busybox";
    private static final String BUSYBOX_TAG = "1.36";

    private static DockerClient dockerClient;
    private DockerService dockerService;
    private final List<String> startedContainerIds = new ArrayList<>();

    @BeforeAll
    static void connect() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(60))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        boolean reachable;
        String reason = null;
        try {
            dockerClient.pingCmd().exec();
            reachable = true;
        } catch (Exception e) {
            reachable = false;
            reason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        assumeTrue(reachable, "Docker daemon not reachable: " + reason);
    }

    @BeforeEach
    void setUp() {
        dockerService = new DockerService(dockerClient);
    }

    @AfterEach
    void cleanup() {
        for (String id : startedContainerIds) {
            try {
                dockerClient.removeContainerCmd(id).withForce(true).exec();
            } catch (Exception ignored) {
            }
        }
        startedContainerIds.clear();
    }

    @Test
    void createAndStart_pullsAndRunsBusybox() {
        ContainerSpec spec = sleepingBusybox("it-run");
        String containerId = dockerService.createAndStart(spec);
        startedContainerIds.add(containerId);

        assertThat(containerId).hasSize(64).matches("^[0-9a-f]+$");
        ContainerStatus status = dockerService.inspect(containerId);
        assertThat(status.state()).isEqualToIgnoringCase("running");
        assertThat(status.startedAt()).isNotNull();
    }

    @Test
    void inspect_reportsExitCodeAfterStop() {
        ContainerSpec spec = sleepingBusybox("it-stop");
        String containerId = dockerService.createAndStart(spec);
        startedContainerIds.add(containerId);

        dockerService.stop(containerId, 1);
        ContainerStatus status = dockerService.inspect(containerId);
        assertThat(status.state()).isEqualToIgnoringCase("exited");
        assertThat(status.exitCode()).isNotNull();
    }

    @Test
    void remove_deletesContainerFromHost() {
        ContainerSpec spec = sleepingBusybox("it-remove");
        String containerId = dockerService.createAndStart(spec);

        dockerService.stop(containerId, 1);
        dockerService.remove(containerId);

        assertThatThrownBy(() -> dockerService.inspect(containerId))
                .isInstanceOf(DockerOperationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createAndStart_failsClearlyOnInvalidImage() {
        ContainerSpec spec = new ContainerSpec(
                "miniorch-no-such-image-zzz",
                "doesnotexist",
                "miniorch-it-bad-" + shortId(),
                Map.of(),
                List.of(),
                Map.of("miniorch.test", "true"));

        assertThatThrownBy(() -> dockerService.createAndStart(spec))
                .isInstanceOf(DockerOperationException.class)
                .hasMessageContaining("miniorch-no-such-image-zzz");
    }

    private static ContainerSpec sleepingBusybox(String label) {
        return new ContainerSpec(
                BUSYBOX,
                BUSYBOX_TAG,
                "miniorch-it-" + label + "-" + shortId(),
                Map.of("MINIORCH_TEST", "1"),
                List.<PortMapping>of(),
                Map.of("miniorch.test", "true", "miniorch.managed", "true"),
                List.of("sleep", "30"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
