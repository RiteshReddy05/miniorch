package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.docker.DockerOperationException;
import com.miniorch.docker.DockerService;
import com.miniorch.persistence.Replica;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HttpProber implements HealthProber {

    private final DockerService dockerService;

    @Override
    public ProbeType supportedType() {
        return ProbeType.HTTP;
    }

    @Override
    public ProbeOutcome probe(Replica replica, ProbeConfig config) {
        long start = System.nanoTime();
        Optional<String> maybeIp = lookupIp(replica.getContainerId());
        if (maybeIp.isEmpty()) {
            return ProbeOutcome.failing("container ip unavailable", elapsedMs(start));
        }
        String url = "http://" + maybeIp.get() + ":" + config.port() + config.path();
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return ProbeOutcome.passing("HTTP " + code + " from " + url, elapsedMs(start));
            }
            return ProbeOutcome.failing("HTTP " + code + " from " + url, elapsedMs(start));
        } catch (java.net.http.HttpTimeoutException ex) {
            return ProbeOutcome.failing("timeout after " + timeout.toSeconds() + "s on " + url, elapsedMs(start));
        } catch (java.io.IOException ex) {
            return ProbeOutcome.failing("io error: " + ex.getMessage(), elapsedMs(start));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProbeOutcome.failing("interrupted", elapsedMs(start));
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
