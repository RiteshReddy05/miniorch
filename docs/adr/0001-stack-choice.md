# ADR 0001 — Spring Boot + docker-java for the control plane

- **Status:** Accepted
- **Date:** 2026-04-26

## Context

MiniOrch needs a backend that:

1. Exposes a REST API for the React UI to declare desired state.
2. Runs a reliable, observable, scheduled background loop that talks to the Docker Engine.
3. Streams logs and metrics over WebSockets to the UI.
4. Persists deployments, container records, and an events audit trail to PostgreSQL.
5. Enforces JWT authentication on every endpoint once auth lands.

The candidates considered were Spring Boot (Java 17) with the `docker-java` client, and Go with `client.NewClientWithOpts` from the official Docker SDK.

## Decision

Use **Spring Boot 3.5.x on Java 17** with **`com.github.docker-java:docker-java` 3.4.x** for Docker Engine access.

> Originally drafted with Boot 3.3.x. Bumped to 3.5.x on the same day because Spring Initializr no longer bootstraps the 3.3 line (compatibility range is now `>=3.5.0`) and the 3.3 series fell out of free OSS patch support in late 2025. 3.5.x is the current production line, runs on Java 17, and is fully API-compatible with the design assumed in this ADR.

## Why Spring Boot

- The reconciliation loop maps cleanly onto `@Scheduled(fixedDelay = "10s")`. Spring's task scheduler gives reliable single-threaded execution, jitter handling, graceful shutdown, and integration with the actuator's `/actuator/scheduledtasks` endpoint — none of which has to be written by hand.
- Spring Security with JWT (jjwt) is the most boring, well-trodden auth path on the JVM. It is a known quantity for the kinds of roles this project is targeted at.
- Spring Data JPA + Hibernate handles the deployment / container / event tables with effectively zero boilerplate, which lets the project budget go to the controller code instead of repository plumbing.
- Spring's WebSocket support handles per-container log streaming with `TextWebSocketHandler` plus a session registry — again a known recipe.
- Testcontainers has first-class Spring Boot support (`@ServiceConnection` since Boot 3.1), which makes integration tests against a real Postgres trivial.

## Why docker-java

- `docker-java` is the most mature JVM client for the Docker Engine API. It covers the full surface MiniOrch needs (list / create / start / stop / inspect / logs / events) and exposes a typed `DockerClient` that is straightforward to mock for unit tests and to drive against a real socket for integration tests.
- The HTTP transport is pluggable; the `httpclient5` transport pairs cleanly with the Boot 3 / Java 17 baseline.
- Other JVM options (`docker-client` from spotify, `kubernetes-client` from fabric8) are either unmaintained or scoped to a different runtime.

## Why not Go

Go would arguably be a more idiomatic language for container tooling — kubelet, containerd, CRI, and the Docker daemon are all written in it, and the official Docker SDK is more ergonomic than `docker-java`. Two reasons it was not picked:

1. The portfolio is targeting Java full-stack roles. A control-plane project written in Go would land less well in those interviews than the same project written in idiomatic Spring Boot, where the interviewer can ask Spring-specific questions and get sharp answers.
2. Switching languages mid-project costs a real chunk of the six-day budget on tooling, package layout, and idioms — surface area that does not contribute to the goal.

## Consequences

- The build will be Gradle (Groovy DSL), packaging a fat jar.
- The deployment story for the project itself is "run the jar on a host that can reach the Docker socket". This is fine for a portfolio piece; production deployment is out of scope.
- The team picking this up needs JDK 17 installed. Acceptable cost for the audience.
- `docker-java` releases on a slower cadence than the Go SDK, so any new Docker Engine endpoints may take a few weeks to appear in the client. None of those endpoints are on the MiniOrch roadmap.
