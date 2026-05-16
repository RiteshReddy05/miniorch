# ADR 0005 — React frontend: storage, polling, no test runner

- **Status:** Accepted
- **Date:** 2026-05-16

## Context

By the end of Day 5a, every backend capability MiniOrch promises (auth, deployments CRUD, scale, reset, events, health checks, CrashLoopBackOff) was exposed over a JWT-protected REST API. What the project did not have was a way to *use* any of it without curl. Day 6's goal was a single-page React UI that exercises the full surface — register / login → list with auto-refresh → create with optional probe config → detail with replicas and events → scale, reset replica, delete — without growing the scope into a second product.

Three decisions in particular needed to be locked early because they shape every page:

1. **Where does the JWT live in the browser?** localStorage, sessionStorage, in-memory, or a refresh-token endpoint?
2. **How do we keep the UI in sync with a backend that converges on its own 10-second clock?** Manual refresh, `setInterval` + `useEffect`, or a query-cache library (`@tanstack/react-query`, SWR)?
3. **Do we add a frontend test runner now, or accept that headless-Chrome live demos are the only verification signal for the UI?**

## Decision

### Token storage: `sessionStorage`

The JWT lives in `sessionStorage` under the key `miniorch.token`. `AuthContext` seeds itself from sessionStorage on mount, writes on login, clears on logout and on the `miniorch:unauthorized` event fired by the axios 401 interceptor. There is no other persistence layer.

The intentional tradeoff is that **closing the tab drops the session**. Reopening the SPA in a fresh tab redirects to `/login`. This is a deliberate security choice: an attacker who can briefly observe the browser process (a malicious extension snapshotting localStorage, a forensic dump) gets a stale token at worst, never a long-lived one. The cost is a worse "I closed my tab" UX, which is acceptable for a developer-facing control plane.

### Auto-refresh: plain `setInterval` in a tiny hook

`hooks/usePolling.js` is ~30 lines. It accepts a fetcher and an interval; it fires once on mount, then every `intervalMs` until unmount; it pauses when `document.hidden` (no API traffic from a backgrounded tab) and immediately re-fetches on `visibilitychange` resume. Both `DeploymentsList` and `DeploymentDetail` poll at 10s, matching the backend's reconciliation cadence so any controller-side convergence shows up in the UI within one tick.

### No frontend test runner

Day 6 shipped without `vitest`, `@testing-library/react`, or any committed frontend tests. Backend stays at 59 tests across 11 suites as the green-signal source. The auth flow, list page, and detail page were verified end-to-end in headless Chrome via Playwright during the live demos for commits 1, 2, and 3, but those scripts are not committed.

## Alternatives considered

- **localStorage for the JWT.** The de-facto default for SPAs and a real UX improvement (close-tab-keeps-session). Rejected for the same reason most security guides warn against it: a persistent token across browser sessions gives any XSS payload a longer window to exfiltrate. We don't have third-party scripts on the page today, but the closer we keep the surface to zero, the safer the next person to add one is.
- **In-memory only, paired with a refresh-token endpoint.** The most security-conscious option, but it needs server-side support we don't have (no `/auth/refresh`, no rotating refresh tokens, no token-revocation list). Adding all of that for a control-plane portfolio project would have eaten another full day and produced almost no operator-visible value.
- **`@tanstack/react-query` for data fetching.** The right choice on a project that grows past a handful of endpoints. It gives us automatic refetch on focus, request deduping, stale-while-revalidate caches, and mutation-paired invalidation for free. Rejected for Day 6 because adding one external dep to handle nine API endpoints is more machinery than payload — the 30-line `usePolling` hook covers the two pages that need polling, and the mutating actions (create, scale, reset, delete) just upsert the returned `DeploymentResponse` into local state directly. Worth reconsidering if a future day adds a tenth list, a fifth detail-style page, or anything that needs cross-component cache sharing.
- **SWR.** Same shape as TanStack Query but smaller. Same rejection reason — the polling we actually need is too thin to justify the dependency.
- **WebSocket-driven updates instead of polling.** Day 5b is going to add a WebSocket endpoint for logs. Eventually the frontend could use it to push deployment / event changes too, getting rid of polling entirely. Out of Day-6 scope; the polling model is correct for now and lets the WebSocket layer focus on log streaming (the one thing it has to do).
- **vitest + @testing-library/react in Day 6.** Tempting because every component is small and pure. Rejected because Day 6's verification budget was already spent on live headless-Chrome demos for commits 1, 2, and 3 — those *did* exercise the real auth flow, the real reconciler convergence (scale from 1 → 3 with `docker ps` confirmation), and the real CrashLoopBackOff trip-and-reset. Component-level unit tests would have been less honest, not more. Adding them as a follow-up commit when the UI stops changing is the right next step.

## Consequences

- **Tab-close = sign-out.** This will surprise users coming from apps that "remember me by default". The Sign-in page should learn to explain this if it ever ships outside a dev context.
- **Polling produces visible API traffic every 10s per open tab.** Acceptable for a single-user dev tool; problematic if MiniOrch ever serves multiple operators with the list page open. The visibility-change pause cuts the cost of idle tabs to zero, which is what matters most.
- **An immediate scale from 1 → 3 takes up to one full poll cycle to reflect "3/3 running" in the UI.** The list state is updated locally with the API's `DeploymentResponse` immediately (so `desiredReplicas` advances on the spot), but the underlying replica statuses (which the backend's reconciler converges asynchronously) lag by up to 10 seconds. The "Auto-refreshes every 10 seconds" header line on `DeploymentsList` is meant to set that expectation. The verification run on commit 2 showed convergence in ~12s.
- **Frontend regressions can ship.** Until the test runner lands, a future change to `AuthContext` or `usePolling` can break the SPA without anything failing in CI. The risk is bounded by the small surface area (≤20 components) and the live-demo scripts in `/tmp/miniorch-e2e-commit*.mjs` from Day 6, which any contributor can re-run against a fresh checkout.
- **The `react-router-dom` dep that has been on the classpath since Day 1 is finally load-bearing.** Routes are now: `/` (auth-aware redirect), `/login`, `/register`, `/deployments`, `/deployments/:id`, and a catch-all 404. `RequireAuth` is the gate.
- **The Day-1 `Landing.jsx`** (the "Backend: OK (v0.1.0)" health check page) is gone — every authenticated call exercises the backend now, so the standalone reachability check is redundant. Its emerald-on-slate-950 aesthetic is preserved in `AuthCard` and the rest of the app.
