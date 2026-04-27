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

**Day 2 — deployment CRUD + Docker integration.** `POST /api/v1/deployments` persists a `Deployment` row plus `Replica` rows and spins up real Docker containers via the `docker` wrapper; `GET` (list / single / events) and `DELETE` (synchronous stop + remove) round out the CRUD. Foundation from Day 1 carries over unchanged.

## Decisions deferred

- **Database migrations:** Day 2 still ships with `spring.jpa.hibernate.ddl-auto: update` so the new entities land without an extra migration step. Flyway gets its own focused commit on Day 3 or Day 5 (write `V1__init.sql` from the current schema, flip `ddl-auto` to `validate`).
- **Spring Security lockdown:** The Day 1 `SecurityConfig` permits all requests. JWT issue / verify and `authenticated()` rules land on Day 5, gated by the `/auth/login` endpoint and bearer-token filter.
- **Reconciliation loop:** Day 2 is request/response only — if a container dies between `POST` and `GET`, the DB still says `RUNNING`. The scheduled reconciler that converges actual to desired is Day 3.
- **Per-replica port offsets:** Day 2 replicas all share the same host port mapping, so `desiredReplicas > 1` collides on the second container. Per-replica ephemeral ports + a load balancer ship later (Day 3 or Day 6 with the UI work).
