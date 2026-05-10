package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TcpProber implements HealthProber {

    private final DockerService dockerService;

    @Override
    public ProbeType supportedType() {
        return ProbeType.TCP;
    }

    @Override
    public ProbeOutcome probe(Replica replica, ProbeConfig config) {
        long start = System.nanoTime();
        Optional<String> maybeIp = lookupIp(replica.getContainerId());
        if (maybeIp.isEmpty()) {
            return ProbeOutcome.failing("container ip unavailable", elapsedMs(start));
        }
        String ip = maybeIp.get();
        int port = config.port();
        int timeoutMs = config.timeoutSeconds() * 1000;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return ProbeOutcome.passing("TCP connect to " + ip + ":" + port, elapsedMs(start));
        } catch (java.net.SocketTimeoutException ex) {
            return ProbeOutcome.failing("TCP timeout after " + config.timeoutSeconds() + "s on " + ip + ":" + port,
                    elapsedMs(start));
        } catch (java.io.IOException ex) {
            return ProbeOutcome.failing("TCP connect failed on " + ip + ":" + port + ": " + ex.getMessage(),
                    elapsedMs(start));
        }
    }

    private Optional<String> lookupIp(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return Optional.empty();
        }
        try {
            return dockerService.getContainerIp(containerId);
        } catch (DockerOperationException ex) {
            return Optional.empty();
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
