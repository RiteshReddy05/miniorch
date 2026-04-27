package com.miniorch.docker;

import com.miniorch.common.PortMapping;

import java.util.List;
import java.util.Map;

public record ContainerSpec(
        String image,
        String tag,
        String containerName,
        Map<String, String> env,
        List<PortMapping> ports,
        Map<String, String> labels,
        List<String> command) {

    public ContainerSpec {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("image is required");
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag is required");
        }
        if (containerName == null || containerName.isBlank()) {
            throw new IllegalArgumentException("containerName is required");
        }
        env = env == null ? Map.of() : Map.copyOf(env);
        ports = ports == null ? List.of() : List.copyOf(ports);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        command = command == null ? List.of() : List.copyOf(command);
    }

    public ContainerSpec(
            String image,
            String tag,
            String containerName,
            Map<String, String> env,
            List<PortMapping> ports,
            Map<String, String> labels) {
        this(image, tag, containerName, env, ports, labels, List.of());
    }

    public String imageRef() {
        return image + ":" + tag;
    }
}
