package com.miniorch.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.miniorch.common.PortMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private static final long PULL_TIMEOUT_SECONDS = 120;
    private static final long START_SETTLE_MILLIS = 500;

    private final DockerClient dockerClient;

    public String createAndStart(ContainerSpec spec) {
        pullImage(spec);
        String containerId = createContainer(spec);
        startContainer(containerId, spec);
        return containerId;
    }

    public ContainerStatus inspect(String containerId) {
        try {
            InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = response.getState();
            Instant startedAt = parseInstant(state.getStartedAt());
            return new ContainerStatus(
                    response.getId(),
                    state.getStatus(),
                    state.getExitCodeLong() == null ? null : state.getExitCodeLong().intValue(),
                    startedAt);
        } catch (NotFoundException e) {
            throw new DockerOperationException("container not found: " + containerId, e);
        } catch (Exception e) {
            throw new DockerOperationException("inspect failed for " + containerId, e);
        }
    }

    public void stop(String containerId, int graceSeconds) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(graceSeconds).exec();
        } catch (NotModifiedException e) {
            log.debug("container {} already stopped", containerId);
        } catch (NotFoundException e) {
            log.debug("container {} already gone", containerId);
        } catch (Exception e) {
            throw new DockerOperationException("stop failed for " + containerId, e);
        }
    }

    public void remove(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException e) {
            log.debug("container {} already removed", containerId);
        } catch (Exception e) {
            throw new DockerOperationException("remove failed for " + containerId, e);
        }
    }

    private void pullImage(ContainerSpec spec) {
        try {
            log.info("pulling image {}", spec.imageRef());
            boolean completed = dockerClient.pullImageCmd(spec.image())
                    .withTag(spec.tag())
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new DockerOperationException("pull timed out for " + spec.imageRef());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerOperationException("pull interrupted for " + spec.imageRef(), e);
        } catch (NotFoundException e) {
            throw new DockerOperationException("image not found: " + spec.imageRef(), e);
        } catch (DockerOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new DockerOperationException("pull failed for " + spec.imageRef(), e);
        }
    }

    private String createContainer(ContainerSpec spec) {
        try {
            List<ExposedPort> exposedPorts = new ArrayList<>();
            List<PortBinding> portBindings = new ArrayList<>();
            for (PortMapping port : spec.ports()) {
                ExposedPort exposed = new ExposedPort(port.containerPort(), protocolOf(port.protocol()));
                exposedPorts.add(exposed);
                portBindings.add(new PortBinding(Ports.Binding.bindPort(port.hostPort()), exposed));
            }

            HostConfig hostConfig = HostConfig.newHostConfig().withPortBindings(portBindings);

            var createCmd = dockerClient.createContainerCmd(spec.imageRef())
                    .withName(spec.containerName())
                    .withLabels(spec.labels())
                    .withEnv(toEnvList(spec.env()))
                    .withExposedPorts(exposedPorts)
                    .withHostConfig(hostConfig);
            if (!spec.command().isEmpty()) {
                createCmd = createCmd.withCmd(spec.command());
            }
            CreateContainerResponse response = createCmd.exec();
            return response.getId();
        } catch (Exception e) {
            throw new DockerOperationException("create failed for " + spec.containerName(), e);
        }
    }

    private void startContainer(String containerId, ContainerSpec spec) {
        try {
            dockerClient.startContainerCmd(containerId).exec();
            try {
                Thread.sleep(START_SETTLE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ContainerStatus status = inspect(containerId);
            if (!"running".equalsIgnoreCase(status.state())) {
                throw new DockerOperationException(
                        "container " + spec.containerName() + " did not stay running (state="
                                + status.state() + ", exitCode=" + status.exitCode() + ")");
            }
        } catch (DockerOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new DockerOperationException("start failed for " + spec.containerName(), e);
        }
    }

    private static List<String> toEnvList(Map<String, String> env) {
        List<String> out = new ArrayList<>(env.size());
        env.forEach((k, v) -> out.add(k + "=" + v));
        return out;
    }

    private static InternetProtocol protocolOf(String protocol) {
        return "udp".equalsIgnoreCase(protocol) ? InternetProtocol.UDP : InternetProtocol.TCP;
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank() || iso.startsWith("0001")) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
