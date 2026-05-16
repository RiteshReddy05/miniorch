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

**Day 6 complete; Day 5b next: WebSocket log streaming + Docker stats metrics.** Day 6 shipped the React frontend that exercises every backend capability — login/register, list with auto-refresh + create modal + scale, detail page with replicas + events tabs + reset, plus toasts, loading skeletons, and a 404 page. The JWT lives in `sessionStorage` (closes-tab-loses-session by design), auto-refresh is plain `setInterval` paused on `document.hidden`, and there is no frontend test runner — the auth, list, and detail flows were verified live in headless Chrome via Playwright during commits 1, 2, and 3 but no committed test suite covers them on every push. Foundation from Days 1–5a carries over unchanged.

## Decisions deferred

- **WebSocket log streaming and Docker stats metrics:** Day 5b — surface `dockerClient.logContainerCmd(...)` over a WebSocket endpoint (the `spring-boot-starter-websocket` dependency has been on the classpath since Day 1, unused), plus a `/metrics` endpoint that fans out `docker stats` per managed container. Auth on the WebSocket handshake will need to extract the bearer token from the query string since browsers do not let you set custom headers on WebSocket upgrades.
- **Frontend tests:** Day 6 shipped without a frontend test runner. Adding vitest + @testing-library/react and porting the headless-Chrome E2E flows (auth, list+create+scale, detail+reset+delete) into committed tests is its own focused commit. Until then, backend stays the green-signal source (59 tests across 11 suites).
- **Per-user deployment ownership and finer-grained RBAC:** Day 5a authenticates the caller and exposes `ROLE_USER` / `ROLE_ADMIN`, but every authenticated user can manage every deployment. Adding `owner_id` to `Deployment`, scoping list/get/update/delete by owner, and gating destructive operations on `ROLE_ADMIN` lands later if at all.
- **Per-replica port offsets:** Replicas all share the same host port mapping, so `desiredReplicas > 1` collides on the second container when ports are declared. Per-replica ephemeral ports + a load balancer ship later (Day 6 with the UI work).
- **Live HTTP/TCP probe verification on Mac:** Container bridge IPs are not routable from the host JVM on Docker Desktop, so the happy probe path is exercised by JDK-local `HttpServer`/`ServerSocket` unit tests rather than against real containers in the live demo. Documented in ADR-0003. The CrashLoopBackOff demo doesn't depend on probes — it's driven by container exits, which are observed correctly via Docker inspect on every platform.
- **`createAndStart` rollback gap (Day 2):** When `DockerService.startContainer` throws (container did not stay running), the started-container ID is lost on the way out, so `DeploymentService.create`'s rollback loop misses it and the container is orphaned. Hit during the Day 4 hello-world demo attempt; cleaned up manually. Worth a small fix on a future day — surface the started ID via a dedicated exception or a partial-result.

## Decisions done

- **Database migrations** — Day 5a. Flyway with `baseline-on-migrate=true` and `baseline-version=1`. `V1__baseline.sql` reproduces the Day 1–4 schema (including the manually-altered `replicas_status_check` six-value CHECK) verbatim from `pg_dump`. `V2__users.sql` adds the users table. `ddl-auto` is now `validate`.
- **Spring Security lockdown** — Day 5a. `SecurityConfig` is STATELESS, `JwtAuthenticationFilter` populates `SecurityContext` from `Authorization: Bearer`, and a custom `AuthenticationEntryPoint` renders 401 in the project's standard `ErrorResponse` shape.
