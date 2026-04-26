package com.miniorch.api.dto;

import com.miniorch.common.PortMapping;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateDeploymentRequest(
        @NotBlank
                @Size(max = 40)
                @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
                        message = "name must be lowercase alphanumeric, may contain hyphens, must start and end with alphanumeric")
                String name,
        @NotBlank
                @Pattern(regexp = "^[a-z0-9._/\\-]+$",
                        message = "image must contain only lowercase letters, digits, dots, slashes, dashes, underscores")
                String image,
        @NotBlank String tag,
        @Min(1) @Max(10) int desiredReplicas,
        Map<String, String> env,
        List<PortMapping> ports) {

    public Map<String, String> envOrEmpty() {
        return env == null ? Map.of() : env;
    }

    public List<PortMapping> portsOrEmpty() {
        return ports == null ? List.of() : ports;
    }
}
