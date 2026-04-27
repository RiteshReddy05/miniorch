package com.miniorch.api;

import com.miniorch.api.dto.CreateDeploymentRequest;
import com.miniorch.api.dto.DeploymentEventResponse;
import com.miniorch.api.dto.DeploymentResponse;
import com.miniorch.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentResponse create(@Valid @RequestBody CreateDeploymentRequest request) {
        return deploymentService.create(request);
    }

    @GetMapping
    public List<DeploymentResponse> list() {
        return deploymentService.list();
    }

    @GetMapping("/{id}")
    public DeploymentResponse get(@PathVariable UUID id) {
        return deploymentService.get(id);
    }

    @GetMapping("/{id}/events")
    public List<DeploymentEventResponse> events(@PathVariable UUID id) {
        return deploymentService.events(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deploymentService.delete(id);
    }
}
