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
| `com.miniorch.controller` | Reconciliation loop and per-deployment convergence logic |
| `com.miniorch.docker` | docker-java wrapper: list, create, start, stop, inspect, logs |
| `com.miniorch.persistence` | JPA entities and repositories for deployments, containers, events |
| `com.miniorch.auth` | JWT issue + verify, Spring Security integration |
| `com.miniorch.config` | Spring configuration beans (security, scheduling, websocket) |

## The reconciliation loop, in plain language

Every ten seconds the loop wakes up and, for each `Deployment` in the database, asks two questions:

1. **What did the user ask for?** Read the desired state — image, replicas, ports, env, health check, restart policy.
2. **What is actually running?** Ask Docker for the list of containers labelled with this deployment's id, and read their status (running, exited, restarting).

If the two views disagree, the loop takes the smallest action that closes the gap:

- Replicas too low → create and start one new container, log an event, return to the loop. Do not try to fix everything at once.
- Replicas too high → stop and remove one extra container, log an event.
- Container exited unexpectedly → schedule a restart with exponential backoff (1s, 2s, 4s, 8s, capped at 60s). After five failures in five minutes, mark the deployment `CrashLoopBackOff` and stop trying until the user intervenes.
- Image changed → roll one container at a time (rolling update), wait for the new one to be healthy, then move on.

The loop is **idempotent**: running it twice in a row with the same desired and actual state produces no actions. This is what makes the model robust — a missed tick or a partial failure does not corrupt the system; the next pass simply picks up where the previous one left off.

The loop is **single-threaded**: only one reconciliation pass runs at a time, and only one action per deployment is taken per pass. This keeps the implementation small and the behaviour predictable. It also caps throughput, which is fine for the design budget of this project.
