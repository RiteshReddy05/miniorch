# ADR 0004 — JWT authentication and Flyway-managed migrations

- **Status:** Accepted
- **Date:** 2026-05-11

## Context

Two pieces of Day-1 debt come due on Day 5a, and they share an underlying property: both move state that has been "implicit and trusted" into something explicit and auditable.

1. **`ddl-auto: update`** worked through Day 4, but only because every entity change was small, additive, and applied during development. Day 4 already exposed a crack: Hibernate's `update` mode does not refresh enum CHECK constraints, so adding `CRASHLOOP_BACKOFF` to `Replica.Status` required a manual `ALTER TABLE replicas DROP CONSTRAINT replicas_status_check; ADD CONSTRAINT … CHECK …`. That edit lived only in the dev database; nothing in the repo would have reproduced it on a fresh check-out. Continuing past Day 4 with `update` would have widened that gap on every subsequent enum change.
2. **`SecurityConfig` permitAll** was the Day-1 stopgap. It let the rest of the project move forward without auth concerns, but the moment the API has user-mutating endpoints (`PATCH /scale`, `DELETE`, `POST /replicas/{i}/reset`) the absence of authentication is the largest unaddressed risk in the codebase.

## Decision

### Schema management

Flyway with `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1`. `V1__baseline.sql` is the verbatim `pg_dump --schema-only --no-owner --no-privileges` of the Day 4 database, including Hibernate's auto-generated constraint names (`fk3sp86rbymfj7ir0weclara7kk`, etc.) so existing dev DBs converge on the same names a fresh DB would produce.

Two paths produce the same schema:

- **Existing populated DB (no `flyway_schema_history` table)**: Flyway sees the schema isn't empty, runs its baseline step, records V1 as `<<Flyway Baseline>>` without executing it, then applies V2 onwards.
- **Fresh empty DB**: Flyway sees no tables, no baseline triggered, V1 runs from scratch, V2 runs after.

`spring.jpa.hibernate.ddl-auto` switches from `update` to `validate`. Hibernate now refuses to start if a column type does not match what the entity declares; it never mutates the schema again.

### Authentication

HS256 JWT bearer tokens, 1-hour TTL, signed with a 32+ character secret read from the `MINIORCH_JWT_SECRET` environment variable. The secret is validated `@NotBlank @Size(min=32)` on `AuthProperties` so a missing or short secret fails the application context at startup. There is no default — running without the env var crashes immediately, not silently with an insecure fallback.

Passwords are bcrypt with strength 10 — fast enough that tests don't drag, strong enough that the cost of a successful database breach scales reasonably with attacker hardware.

The token payload carries `sub` (UUID user id), `username`, `role`, `iat`, and `exp`. Role is the bare enum name; the `ROLE_` prefix Spring Security expects is added in `JwtAuthenticationFilter` when building authorities, not in the token claim itself.

The filter swallows `JwtException` on malformed, tampered, or expired tokens and lets the request continue without authentication. The `AuthenticationEntryPoint` decides whether the path needed auth and writes a 401 in the project's standard `ErrorResponse` shape if so. This separation means a public endpoint (`/api/v1/health`) still serves a request that arrives with a bad `Authorization` header — the filter is purely an "authenticate if you can" component.

Sessions are STATELESS. CSRF is disabled because bearer-token authentication is inherently CSRF-resistant.

## Alternatives considered

- **Server-side sessions with `JSESSIONID` cookies.** Simpler initially, but pulls in CSRF handling, session-store decisions, and a worse story for an SPA front-end that wants to set a custom `Authorization` header. Rejected.
- **OAuth2 / Keycloak / Auth0.** The right answer at scale, drastically over-scoped for a portfolio project that needs to authenticate one user against one database. Rejected — the cost of running an IdP for the demo exceeds the value.
- **BCrypt strength 12.** More secure per password (~4× slower than strength 10). The bottleneck shows up in tests: each `passwordEncoder.encode(...)` in an integration test adds noticeable wall-clock time. Strength 10 is the conventional sweet spot for projects without exotic threat models. Rejected the bump.
- **HS512 or RS256 instead of HS256.** HS512 has no practical advantage at our scale (HS256 with a 256-bit key has 128-bit collision resistance, which is more than enough). RS256 would be worth it if we had a separate verifying party that should not hold the signing key; we have one process, so symmetric is simpler. Rejected.
- **Embedding the role check at the controller layer.** Tempting because every endpoint currently behaves identically across roles, but moving role checks out of `SecurityConfig` couples authorization to controller code. Kept the centralized config so adding `ROLE_ADMIN`-only endpoints later is one line.
- **Generating `V1__init.sql` by hand instead of from `pg_dump`.** Cleaner SQL output, but invites silent divergence from the live dev DB. Verbatim dump + a careful diff against the live schema is the more honest baseline. The ugly Hibernate-hashed constraint names are the price of that honesty.

## Consequences

- **Every API call now needs a token.** Existing scripts that did `curl /api/v1/deployments` directly will 401 until they're updated to do `register → login → use token`. README has the recipe.
- **Secret rotation invalidates every outstanding token.** That's a feature, not a bug — there's no in-band refresh, and there's no way to leak the old signing key once the env var changes. Day 5b's WebSocket handshake will need the same secret to validate the bearer token passed via the upgrade query string.
- **Schema changes go through Flyway from here on.** Adding a column means writing `V3__add_x.sql`, not changing an entity and hoping `update` keeps up. With `validate` enabled, Hibernate cannot mask a forgotten migration with an automatic `ALTER`.
- **Sub-second precision on `expiresAt` is gone.** The JWT `exp` claim is `NumericDate` per RFC 7519 — seconds since epoch. `JwtTokenProvider` truncates `expiresAt` to seconds when issuing, so `LoginResponse.expiresAt` matches what a client would compute from decoding the token. Worth documenting because the alternative — exposing the nanosecond-precision pre-truncation value — would mislead callers comparing it to `iat` + `ttl`.
- **The `spring-boot-starter-security` auto-config still wires an in-memory user with a generated password.** It is dead code — our `SecurityFilterChain` owns the request handling and never hits the in-memory `UserDetailsService`. The startup-log noise stays for now; suppressing it requires excluding `UserDetailsServiceAutoConfiguration`, which is one line for cosmetic value and lands later if it ever becomes irritating.
