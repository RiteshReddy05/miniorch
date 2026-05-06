package com.miniorch.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScaleRequest(@Min(1) @Max(10) int desiredReplicas) {
}
