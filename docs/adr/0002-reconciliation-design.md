# ADR 0002 — Scheduled reconciliation with per-deployment locks and intent/action separation

- **Status:** Accepted
- **Date:** 2026-05-07

## Context

MiniOrch needs to keep the actual Docker state of each deployment in sync with the user's declared desired state, even when:

- The user mutates desired state at any time (`POST`, `PATCH /scale`, `DELETE`).
- A container exits unexpectedly between request cycles.
- The Docker daemon is briefly unavailable, or a single replica's `create` call fails part-way through a multi-replica deployment.
- The control-plane process restarts and re-reads its state from Postgres.

The control loop has to be cheap to reason about, cheap to test, and cheap to operate. We do not want a request handler to ever block on a slow Docker call, and we do not want the API to be the only path that touches Docker — that would couple the user's request latency to the daemon's latency and would make recovery after a process restart awkward.

## Decision

Run a **scheduled fixed-delay controller** (10s) that does per-deployment convergence, with **per-deployment in-process locks** (`DeploymentLockManager`), and an **intent vs action** split between the API and the controller.

The pieces:

- **`ReconciliationLoop`** — `@Scheduled(fixedDelay = 10_000, initialDelay = 5_000)`. Loads all deployments, takes a non-blocking lock per deployment id, delegates to `DeploymentReconciler.reconcileOne(UUID)`. If a deployment is currently being mutated by the API, it is skipped this tick and picked up on the next.
- **`DeploymentReconciler`** — `@Transactional` per call. For each deployment, runs four phases in order: observe (inspect Docker), attempt restarts (with backoff gate), converge (spawn or remove to match desired), emit transition (compute status, write event if it changed).
- **`DeploymentLockManager`** — `ConcurrentHashMap<UUID, ReentrantLock>` with `tryLock(id, timeout)` / `unlock(id)` / `evict(id)`. The same lock manager guards both API mutations (which use a 5-second timeout) and the reconciliation loop (which uses `Duration.ZERO` and skips on contention).
- **`BackoffCalculator`** — exponential backoff `2^attempt` seconds, capped at 30. Used by `DeploymentReconciler` to decide whether an `EXITED` replica is allowed to be restarted on this tick.
- **Intent vs action.** `POST /deployments` and `DELETE /deployments/{id}` synchronously call Docker because the user expects an immediate effect. `PATCH /deployments/{id}/scale` does not — it only updates `desiredReplicas` and writes `DEPLOYMENT_SCALED`. The reconciler closes the gap on its next pass. This is the same separation as `kubectl scale` against the Kubernetes API server vs. the controller manager.

## Consequences

- **Convergence is bounded by the loop interval, not by request latency.** A scale change takes up to ~10 seconds to materialise, which is acceptable for this project's design budget. If we later need faster reaction, we can wake the loop on demand without changing the per-deployment logic.
- **The scheduler is initially single-threaded.** One reconciliation pass at a time, one deployment at a time within a pass. We may move to a fixed-size pool keyed by deployment id later, but only when there is a real workload that justifies the complexity.
- **Partial-failure isolation comes from `@Transactional` on `reconcileOne`** plus per-deployment locks. A Docker error during one deployment's pass logs an event, leaves the row in a sensible status (`FAILED` / `Degraded`), and does not affect the other deployments in the same tick.
- **Append-only events form the audit trail.** Every controller decision (`REPLICA_STARTED`, `REPLICA_REMOVED`, `REPLICA_RESTART_SCHEDULED`, `REPLICA_RESTART_ATTEMPTED`, `DEPLOYMENT_SCALED`, `DEPLOYMENT_HEALTHY`, `DEPLOYMENT_DEGRADED`, `ERROR`) writes a row, and `GET /events` is the user-visible explanation of what the controller did. This is also how the unit tests assert on behaviour without inspecting Docker state.
- **The model survives a process restart.** Desired state is in Postgres. On boot, `ReconciliationLoop` simply runs and converges. There is no in-memory work queue to lose.

## Alternatives considered

- **Event-driven (Docker events stream).** Would react faster than 10s polling but couples correctness to never missing a daemon event. Polling is more robust and the latency is fine for the project's goals; we may layer event-driven wake-ups on top later as an optimisation.
- **Synchronous scaling in the request handler.** Simpler from the API's point of view but couples request latency to Docker latency, requires holding a transaction open across an external call, and makes recovery-after-restart awkward. Rejected.
- **Global lock instead of per-deployment lock.** Smaller surface but serialises every reconciliation and every API mutation. Rejected — the deployment id is the natural concurrency boundary.
