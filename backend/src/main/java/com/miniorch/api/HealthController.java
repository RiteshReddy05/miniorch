package com.miniorch.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String VERSION = "0.1.0";

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "version", VERSION);
    }
}
