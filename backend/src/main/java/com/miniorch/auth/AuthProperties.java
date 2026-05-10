package com.miniorch.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "miniorch.auth")
public record AuthProperties(
        @NotBlank @Size(min = 32, message = "jwt-secret must be at least 32 characters") String jwtSecret) {
}
