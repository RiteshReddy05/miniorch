# ADR 0003 — Per-deployment health probes and CrashLoopBackOff

- **Status:** Accepted
- **Date:** 2026-05-10

## Context

Day 4 needs to answer two related questions that the Day 3 reconciler cannot:

1. **Is this replica's application actually healthy?** "The Docker container is running" is too weak. A web server can be up but returning 503; a TCP listener can be open but stuck in a half-closed state; a long-lived process can be alive but unable to serve. We want HTTP and TCP probes per replica, configurable per deployment, with a sensible default that does not change Day 3 behaviour for existing clients.
2. **When do we stop trying to fix a replica that keeps breaking?** A pull-image-then-crash loop with exponential backoff is unbounded. We want the same circuit breaker Kubernetes ships: after five failures inside a sliding five-minute window, declare the replica `CrashLoopBackOff`, stop restarting and probing it, and let the operator reset it once the underlying issue is fixed.

These two are tied together because probe failures and container exits both feed the same circuit-breaker counter — they are different signals of the same underlying condition.

## Decision

### Probes

Three probe types, configurable per deployment via `ProbeConfig` on `CreateDeploymentRequest` and persisted as a jsonb column on `Deployment`:

- **`HTTP`** — `GET http://<containerIp>:<port><path>`, expect 2xx. Uses the JDK's `java.net.http.HttpClient` with `connectTimeout` and request `timeout` both bound to `ProbeConfig.timeoutSeconds`.
- **`TCP`** — Open a socket to `<containerIp>:<port>` with `SO_CONNECT_TIMEOUT` set to `timeoutSeconds`. A successful connect is `PASSING`.
- **`DOCKER`** *(default)* — Trust the Docker daemon's reported state. Identical behaviour to Day 3 when no probe is supplied. This is what makes the change additive: every existing client that omits the `probe` field sees no behaviour difference.

Probes execute **from inside the Spring Boot process**, against the container's bridge IP discovered via `DockerService.getContainerIp()`. The IP lookup reads `NetworkSettings.getIpAddress()` and falls through to the `Networks` map for user-defined networks.

The reconciler runs probes as a fourth phase (`runProbes`), inserted between `observe` and `attemptRestarts`. It probes only `RUNNING` replicas — `PENDING`, `EXITED`, `FAILED`, `REMOVED`, and `CRASHLOOP_BACKOFF` are skipped. Container exits are tracked separately by `observe`, so probing `EXITED` replicas would double-count failures, and probing the others has no useful semantics. A per-replica interval gate (`lastProbeAt + ProbeConfig.intervalSeconds > now` ⇒ skip silently) keeps the probe rate bounded; with the default 10 s interval and a 10 s reconciliation tick that is one probe per replica per cycle.

### Probe-level threshold

`ProbeConfig.failureThreshold` (default 3) sits between raw probe outcomes and the deployment-visible `lastProbeResult`. The reconciler increments `consecutiveFailures` on every failing outcome and only flips `lastProbeResult` from `PASSING`/`NOT_PROBED` to `FAILING` when the count reaches the threshold. A passing outcome zeroes the counter. This soaks up transient blips (a single 503 during a deploy, a momentary connection refused during warmup) without spamming `HEALTH_CHECK_FAILED` events. Events fire only on the actual `PASSING ↔ FAILING` transition.

### CrashLoopBackOff

A new `CRASHLOOP_BACKOFF` value joins `Replica.Status` (the column had to grow from `varchar(16)` to `varchar(32)` to fit it). Every fresh failure event appends a timestamp to a per-replica `failureWindow` jsonb column. "Failure" means one of three things, captured by a single `onFailure(...)` helper inside the reconciler:

- A container observed transitioning from `RUNNING`/`PENDING` to `EXITED` in `observe` (a "fresh" exit — repeated `EXITED` reads do not double-count).
- A `DockerOperationException` thrown during a restart attempt in `attemptRestarts` (the create or start step itself failed).
- The probe-level `PASSING/NOT_PROBED → FAILING` transition described above.

Each append trims entries older than five minutes and caps the list at 50 to prevent unbounded growth. When the trimmed window holds five or more entries, the replica flips to `CRASHLOOP_BACKOFF` and `CRASHLOOP_BACKOFF_TRIPPED` is recorded with the latest reason.

A replica in `CRASHLOOP_BACKOFF` is "do not touch": `observe`, `runProbes`, and `attemptRestarts` all early-skip it. `converge` still counts it as an active replica, so a deployment with one crashlooping replica does not trigger a fresh spawn that would inherit the same broken image.

### Reset

`POST /api/v1/deployments/{id}/replicas/{replicaIndex}/reset` is the un-stick lever. Same lock contract as `scale` (5 s timeout, 503 on contention). Validates the replica index exists (404 otherwise) and the replica is currently in `CRASHLOOP_BACKOFF` (400 otherwise). Clears `failureWindow`, `restartCount`, `consecutiveFailures`, `lastError`, `probeDetails`, sets `lastProbeResult` back to `NOT_PROBED`, and flips status to `PENDING`. The next reconciliation tick observes the stale containerId, finds the container gone, and goes through a normal restart cycle. Records `CRASHLOOP_BACKOFF_RESET`.

## Consequences

- **Convergence latency for probe-driven flips is bounded by the reconciler tick (10 s) plus the configured `intervalSeconds`.** The validator caps `intervalSeconds` at `[5, 300]`. Anything below 10 is silently capped by the tick frequency — granularity is bounded by the loop, not by the probe configuration. Worth knowing when wiring up alerting.
- **HTTP/TCP probes do not work end-to-end on Docker Desktop for Mac and Windows.** The bridge network `172.17.0.x` lives inside the LinuxKit VM and is not routable from the host JVM, so probes against real container IPs time out and report `FAILING` regardless of application health. The probers' logic is exercised against JDK-local `HttpServer`/`ServerSocket` instances in unit tests, and the CrashLoopBackOff live demo is driven by container exits (observed via Docker inspect, which works correctly on every platform). Production-style verification of the probe happy path requires the JVM to share a Docker network with the containers it probes — either run the backend itself in `docker compose` on the same network, or run the demo on Linux. **We accept this limitation rather than container the backend** because that change has scope (a `Dockerfile`, a service definition, env-var plumbing) that does not pay back inside Day 4. Revisit when packaging the project for distribution.
- **`docker exec`-based probes were considered and rejected.** The proposal was: `docker exec -it <cid> sh -c "curl -f http://localhost:..."`. That works on Mac because it runs inside the container, but each probe spawns a new exec session, copies the binary in, and tears it down — at five-second probe intervals across dozens of replicas the daemon load is noticeable, and the operational fingerprint (an exec session per probe in the daemon's audit log) is ugly. We took the bridge-IP approach plus the documented Mac limitation instead.
- **Failure semantics are unified.** Container exits, failed restart attempts, and probe-FAILING transitions all flow through the same `onFailure` helper and the same `failureWindow`. That keeps the trip rule honest: the replica fails five distinct times in five minutes. It also means a perpetually image-pull-failing replica still trips CrashLoopBackOff (each restart attempt is a discrete failure), which is what the operator wants.
- **Reset is per-replica, not per-deployment.** A deployment with `desiredReplicas=3` where only replica 1 has crashlooped is unstuck by `POST /replicas/1/reset` — the other two are not disturbed, and converge has not been spawning extras to "replace" the stuck one (`CRASHLOOP_BACKOFF` is still active). This matches the operator mental model where the replica is the unit of failure.

## Alternatives considered

- **Probe from a sidecar container per deployment.** Solves the Mac quirk but doubles container count, requires a network for inter-container traffic, and introduces a sidecar lifecycle problem. Rejected — too much machinery for the design budget.
- **Probe via Docker daemon's `HEALTHCHECK` directive in the image.** The daemon already runs HEALTHCHECK on a schedule and exposes the result via inspect. Tempting because it sidesteps the bridge-IP issue. Rejected because it ties health semantics to the image build (operators cannot change a probe without rebuilding) and the daemon's HEALTHCHECK runner offers no per-probe timeout we can override from the control plane.
- **Trip on `restartCount` rather than a sliding window.** Simpler — `restartCount >= N` is one comparison. Rejected because it does not distinguish "five exits in five minutes" (broken) from "five exits over three days" (occasional flakiness, recovered each time). The window matches K8s and matches what an operator would draw on a whiteboard.
