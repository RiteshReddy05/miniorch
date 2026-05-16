# MiniOrch Architecture

## High-level diagram

```
                    ┌──────────────────────────────┐
                    │       React + Vite UI         │
                    │     (browser, port 5173)      │
                    └──────────┬────────────────────┘
                               │  HTTP  /  WebSocket
                               ▼
                    ┌──────────────────────────────┐
                    │     Spring Boot API           │
                    │      (JVM, port 8080)         │
                    │                               │
                    │  ┌─────────────────────────┐  │
                    │  │   REST + WS controllers │  │
                    │  └────────────┬────────────┘  │
                    │               │               │
                    │  ┌────────────▼────────────┐  │
                    │  │   Reconciliation loop   │  │
                    │  │   (@Scheduled, 10s)     │  │
                    │  └──┬──────────────────┬───┘  │
                    │     │                  │       │
                    │  ┌──▼─────────┐   ┌────▼────┐ │
                    │  │ Docker cli │   │ JPA     │ │
                    │  │ (docker-   │   │ repos   │ │
                    │  │  java)     │   │         │ │
                    │  └──┬─────────┘   └────┬────┘ │
                    └─────┼──────────────────┼──────┘
                          │                  │
                          ▼                  ▼
                ┌──────────────────┐   ┌──────────────┐
                │  Docker Engine   │   │  PostgreSQL  │
                │  (unix socket)   │   │  (port 5432) │
                └──────────────────┘   └──────────────┘
```

## Modules

| Package | Responsibility |
|---|---|
| `com.miniorch.api` | REST + WebSocket controllers — translates HTTP into service calls |
| `com.miniorch.service` | Deployment business logic, mappers between DTOs and entities |
| `com.miniorch.controller` | Reconciliation loop, per-deployment convergence, lock manager, backoff policy, health probers |
| `com.miniorch.docker` | docker-java wrapper: list, create, start, stop, inspect, logs |
| `com.miniorch.persistence` | JPA entities and repositories for deployments, replicas, events, users |
| `com.miniorch.auth` | User entity + repo, register/login service, JWT issue + verify, bearer-token filter |
| `com.miniorch.config` | Spring configuration beans (security filter chain, 401 entry point) |
| `com.miniorch.common` | Shared value types referenced from multiple layers (`PortMapping`, `ProbeConfig`, `ProbeType`) |

Schema is managed by Flyway from `backend/src/main/resources/db/migration`. Hibernate runs in `ddl-auto: validate` so a drift between entity and column types fails the application context at startup instead of letting the ORM mutate the live schema. `V1__baseline.sql` reproduces the Day 1–Day 4 schema exactly; subsequent versions (starting with `V2__users.sql`) add new tables.

The React + Vite frontend (`frontend/`) is a thin client over the REST API — no business logic, no WebSocket yet. Every page is a small `useState` + a `usePolling` hook that re-fetches on a 10s cadence (matched to the reconciliation loop) and pauses when `document.hidden`. The JWT lives in `sessionStorage`, an axios request interceptor attaches it as `Authorization: Bearer`, and a response interceptor maps any 401 to a `miniorch:unauthorized` window event that the auth context turns into a logout + toast + redirect. Design rationale is in `docs/adr/0005-frontend.md`.

## The reconciliation loop, in plain language

Every ten seconds the loop wakes up and, for each `Deployment` in the database, asks two questions:

1. **What did the user ask for?** Read the desired state — image, replicas, ports, env, restart policy.
2. **What is actually running?** Ask Docker for the container backing each replica row and read its status (running, exited, restarting).

If the two views disagree, the loop takes the smallest action that closes the gap:

- Replicas too low → create and start one new container per missing replica, log an event.
- Replicas too high → stop and remove the highest-indexed extras, log an event.
- Container exited unexpectedly → schedule a restart with exponential backoff (1s, 2s, 4s, 8s, capped at 30s). Restart attempts increment a counter on the replica row.
- Image changed → roll one container at a time (rolling update), wait for the new one to be healthy, then move on. *(Rolling update lands in a later phase.)*

The loop is **idempotent**: running it twice in a row with the same desired and actual state produces no actions. This is what makes the model robust — a missed tick or a partial failure does not corrupt the system; the next pass simply picks up where the previous one left off.

The loop is **single-threaded**: only one reconciliation pass runs at a time, and a per-deployment lock guards each per-deployment pass. This keeps the implementation small and the behaviour predictable. It also caps throughput, which is fine for the design budget of this project.

## Reconciliation loop — per-deployment flow

`ReconciliationLoop` is the `@Scheduled` entry point. It does very little on its own — it loads the deployments, takes the per-deployment lock, and delegates the actual decision logic to `DeploymentReconciler.reconcileOne(UUID)`.

```
              ┌───────────────────────────────────────┐
              │  @Scheduled tick (every 10s)          │
              │  ReconciliationLoop.reconcile()       │
              └──────────────────┬────────────────────┘
                                 │
                                 ▼
                    deploymentRepository.findAll()
                                 │
                                 ▼
                  for each Deployment d:
                                 │
                                 ▼
              ┌──────────────────────────────────────┐
              │  lockManager.tryLock(d.id, 0s)       │
              │     │                                │
              │     ├── busy?  → skip this tick      │
              │     │                                │
              │     ▼                                │
              │  reconciler.reconcileOne(d.id)       │
              │     │                                │
              │     ├── 1. observe                   │
              │     │     skip REMOVED + CRASHLOOP   │
              │     │     for each remaining replica:│
              │     │       dockerService.tryInspect │
              │     │       map state → Replica.Status
              │     │       fresh exit?              │
              │     │         → onFailure → window++ │
              │     │                                │
              │     ├── 2. runProbes                 │
              │     │     skip non-RUNNING replicas  │
              │     │     skip if intervalSeconds    │
              │     │       has not elapsed          │
              │     │     dispatch by ProbeType:     │
              │     │       HTTP / TCP / DOCKER      │
              │     │     PASSING  → reset failures, │
              │     │       FAILING→PASSING transit  │
              │     │       writes HEALTH_CHECK_PASS │
              │     │     FAILING  → bump counter,   │
              │     │       at threshold→FAILING,    │
              │     │       writes HEALTH_CHECK_FAIL │
              │     │       + onFailure → window++   │
              │     │                                │
              │     ├── 3. attemptRestarts           │
              │     │     for EXITED/FAILED replicas:│
              │     │       backoff gate? skip       │
              │     │       else: stop+remove+create │
              │     │       write RESTART_SCHEDULED  │
              │     │             RESTART_ATTEMPTED  │
              │     │       on failure → onFailure   │
              │     │                                │
              │     ├── 4. converge                  │
              │     │     actual < desired → spawn   │
              │     │     actual > desired → remove  │
              │     │       (highest index first)    │
              │     │     write REPLICA_STARTED      │
              │     │           REPLICA_REMOVED      │
              │     │                                │
              │     ├── 5. emitTransitionIfChanged   │
              │     │     compute status from        │
              │     │     active replicas            │
              │     │     diff vs lastObservedStatus │
              │     │     write DEPLOYMENT_HEALTHY / │
              │     │           DEPLOYMENT_DEGRADED  │
              │     │                                │
              │     │     onFailure (private):       │
              │     │       window.add(now);         │
              │     │       drop ts < now-5min;      │
              │     │       cap at 50 entries;       │
              │     │       size >= 5 →              │
              │     │         status=CRASHLOOP_BACKOFF│
              │     │         write CRASHLOOP_       │
              │     │           BACKOFF_TRIPPED      │
              │     │                                │
              │  finally: lockManager.unlock(d.id)   │
              └──────────────────────────────────────┘
                                 │
                                 ▼
                  next deployment, or end of pass
```

A few invariants the flow relies on:

- **Lock granularity is the deployment id**, not a global mutex, so reconciliation of unrelated deployments can run on different ticks without serialising.
- **Mutating writes happen inside `@Transactional`** on `reconcileOne`, so a partial failure rolls back the in-memory entity state cleanly.
- **Events are append-only**. Every action that changed something is recorded in `DeploymentEvent`, which is what `GET /events` returns. The events feed is the user-visible audit trail of what the controller decided to do and why.
- **Intent vs action is separated.** `PATCH /scale` only updates `desiredReplicas` and writes `DEPLOYMENT_SCALED`. The actual container churn happens later, in `converge`, on a normal reconciliation tick — which means the API stays fast and the controller stays in charge.
- **Health and crash-loop state is per-replica.** The `probe` field is on the deployment, but `lastProbeResult`, `consecutiveFailures`, and `failureWindow` live on `Replica`. The CrashLoopBackOff state is therefore replica-scoped — one replica being stuck does not pin the others, and `POST /replicas/{index}/reset` un-sticks just that one.

## Authentication

Every endpoint under `/api/v1/deployments/**` is locked down behind a JWT bearer token. `/api/v1/health`, `POST /api/v1/auth/register`, and `POST /api/v1/auth/login` are the only public paths.

```
            HTTP request
                 │
                 ▼
        ┌─────────────────────────────┐
        │  JwtAuthenticationFilter    │
        │  (OncePerRequestFilter)     │
        │                             │
        │  read Authorization header  │
        │   ├─ no Bearer prefix:      │
        │   │    continue chain       │
        │   ├─ valid token:           │
        │   │    parse claims,        │
        │   │    set                  │
        │   │    SecurityContext      │
        │   │    with                 │
        │   │    ROLE_USER /          │
        │   │    ROLE_ADMIN,          │
        │   │    continue chain       │
        │   └─ invalid token:         │
        │        log debug,           │
        │        continue chain       │
        │        without context      │
        └────────────┬────────────────┘
                     │
                     ▼
        ┌─────────────────────────────┐
        │  authorizeHttpRequests      │
        │                             │
        │  /api/v1/health        →    │
        │     permitAll               │
        │  /api/v1/auth/**       →    │
        │     permitAll               │
        │  anyRequest()          →    │
        │     authenticated()         │
        └────────────┬────────────────┘
                     │
        ┌────────────┴────────────────┐
        │ auth passed?                │
        │   yes → controller          │
        │   no  → AuthenticationEntryPoint
        │         writes 401 JSON     │
        └─────────────────────────────┘
```

Sessions are STATELESS — no `JSESSIONID` cookie, no server-side session store; the JWT *is* the session. CSRF is disabled because bearer-token authentication is inherently CSRF-resistant (browsers do not auto-attach Authorization headers cross-origin the way they do cookies). The 401 from a missing or rejected token is rendered by a custom `AuthenticationEntryPoint` so the response shape matches every other `ErrorResponse` in the API: `{"error": "...", "details": []}`.

The token payload carries `sub` (userId UUID), `username`, `role`, `iat`, and `exp`. The role claim is the bare enum name (`USER` / `ADMIN`); the filter adds the `ROLE_` prefix when constructing Spring authorities so the JWT stays clean. Tokens are HS256, signed with `MINIORCH_JWT_SECRET` (validated `@NotBlank @Size(min=32)` on application startup), valid for one hour, and not refreshable in-band — clients re-login to renew.
