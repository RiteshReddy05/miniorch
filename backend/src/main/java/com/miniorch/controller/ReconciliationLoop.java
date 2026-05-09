package com.miniorch.controller;

import com.miniorch.persistence.Deployment;
import com.miniorch.persistence.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationLoop {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLockManager lockManager;
    private final DeploymentReconciler reconciler;

    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void reconcile() {
        List<Deployment> deployments = deploymentRepository.findAll();
        if (deployments.isEmpty()) {
            log.debug("reconcile pass: no deployments");
            return;
        }
        log.debug("reconcile pass: {} deployment(s)", deployments.size());
        for (Deployment deployment : deployments) {
            UUID id = deployment.getId();
            if (!lockManager.tryLock(id, Duration.ZERO)) {
                log.debug("reconcile pass: skipping {} (locked)", id);
                continue;
            }
            try {
                reconciler.reconcileOne(id);
            } catch (Exception ex) {
                log.warn("reconcile pass: error on {} — continuing", id, ex);
            } finally {
                lockManager.unlock(id);
            }
        }
    }
}
