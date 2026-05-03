package com.miniorch.controller;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DeploymentLockManager {

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public boolean tryLock(UUID id, Duration timeout) {
        ReentrantLock lock = locks.computeIfAbsent(id, k -> new ReentrantLock());
        try {
            return lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(UUID id) {
        ReentrantLock lock = locks.get(id);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void evict(UUID id) {
        ReentrantLock lock = locks.get(id);
        if (lock == null) {
            return;
        }
        if (lock.isHeldByCurrentThread() || lock.isLocked()) {
            return;
        }
        locks.remove(id, lock);
    }
}
