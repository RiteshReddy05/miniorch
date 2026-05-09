package com.miniorch.controller;

import com.miniorch.common.ProbeConfig;
import com.miniorch.common.ProbeType;
import com.miniorch.persistence.Replica;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HealthProbeRunner {

    private final Map<ProbeType, HealthProber> probersByType;

    public HealthProbeRunner(List<HealthProber> probers) {
        this.probersByType = probers.stream()
                .collect(Collectors.toUnmodifiableMap(HealthProber::supportedType, Function.identity()));
    }

    public ProbeOutcome probe(Replica replica, ProbeConfig config) {
        HealthProber prober = probersByType.get(config.type());
        if (prober == null) {
            throw new IllegalStateException("no prober registered for type " + config.type());
        }
        return prober.probe(replica, config);
    }
}
