# MiniOrch — Working Conventions

A Kubernetes-style container orchestration platform built on top of the Docker Engine: declared desired state in Postgres, a scheduled reconciliation loop that converges actual state via docker-java, REST + WebSocket API for a React UI.

## Stack

- **Backend:** Spring Boot 3.5.x, Java 17, Gradle (Groovy DSL)
- **Container control:** docker-java 3.4.x (httpclient5 transport)
- **Database:** PostgreSQL 16
- **Frontend:** React 18, Vite 5, TailwindCSS 3, axios, react-router-dom 6, lucide-react
- **Real-time:** Spring WebSocket
- **Auth:** JWT via Spring Security (jjwt 0.12.x)
- **Tests:** JUnit 5, Mockito, Testcontainers
- **Local dev:** docker-compose for Postgres

## Conventions

- **Commits:** Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, `perf:`, `ci:`). Subject under 72 chars; body explains *why*, not what. No squashing — each logical change is its own commit.
- **Attribution:** No mentions of AI assistants, generators, or "AI-assisted" anywhere — commits, code, comments, docs, PR descriptions. Everything is authored as the human.
- **In-code TODOs:** Prohibited. If a piece of work is deferred, it goes under "Decisions deferred" in this file (or in an ADR), not as a `// TODO` in the source.
- **Library versions:** Pin to the latest stable release at the time the dependency is added. Note the chosen version in the commit body that introduces it.
- **Tests:** The `controller` module is test-required — every reconciliation rule has unit coverage and the loop is exercised against a real Docker daemon via Testcontainers. Other modules follow the standard 80 % coverage target.
- **Files:** Keep modules small. Single class per file. 200–400 lines is normal, 800 is the cap.
- **Comments:** Default to none. Add a short comment only when the *why* is non-obvious from the code.

## Module map

| Package | Role |
|---|---|
| `com.miniorch.api` | REST + WebSocket controllers, request/response DTOs, exception handler |
| `com.miniorch.service` | Deployment business logic, mappers between DTOs and entities |
| `com.miniorch.controller` | Reconciliation loop, per-deployment convergence |
| `com.miniorch.docker` | docker-java wrapper |
| `com.miniorch.persistence` | JPA entities and repositories |
| `com.miniorch.auth` | JWT issue + verify, Spring Security wiring |
| `com.miniorch.config` | Spring configuration beans |
| `com.miniorch.common` | Shared value types referenced from multiple layers (e.g. `PortMapping`) |

## Current phase

**Day 3 complete; Day 4 next: HTTP/TCP health checks and CrashLoopBackOff (5 failures in 5 minutes → stop restarting).** Day 3 added the scheduled reconciliation loop (`@Scheduled(fixedDelay = 10s)` per-deployment convergence with exponential backoff, per-deployment locks, and append-only event audit), the `PATCH /api/v1/deployments/{id}/scale` intent endpoint, and unit coverage for the four reconciliation cases plus status-transition emission. Foundation from Days 1–2 carries over.

## Decisions deferred

- **Database migrations:** Still shipping with `spring.jpa.hibernate.ddl-auto: update`. Flyway lands on Day 5 (write `V1__init.sql` from the current schema, flip `ddl-auto` to `validate`).
- **Spring Security lockdown:** The Day 1 `SecurityConfig` permits all requests. JWT issue / verify and `authenticated()` rules land on Day 5, gated by the `/auth/login` endpoint and bearer-token filter.
- **Per-replica port offsets:** Replicas all share the same host port mapping, so `desiredReplicas > 1` collides on the second container when ports are declared. Per-replica ephemeral ports + a load balancer ship later (Day 6 with the UI work).
- **Health checks and CrashLoopBackOff:** Day 4 — HTTP/TCP probes per replica, plus a 5-failures-in-5-minutes circuit breaker that pins a deployment in `CrashLoopBackOff` and stops further restart attempts until the user intervenes.
