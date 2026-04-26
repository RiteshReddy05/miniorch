# MiniOrch

A container orchestration platform inspired by Kubernetes. Manage Docker containers through a web UI with reconciliation-based control loop, health checks, auto-restart, and live log streaming.

🚧 Under active development.

## Local development

### Prerequisites

- Java 17
- Node 18+
- Docker Desktop (or any Docker Engine reachable on the local socket)

### Run

Three processes, in order. Each in its own terminal.

1. **Postgres** — start the dev database container:

   ```sh
   docker compose up -d postgres
   ```

2. **Backend** — Spring Boot API on port 8080:

   ```sh
   cd backend && ./gradlew bootRun
   ```

3. **Frontend** — Vite dev server on port 5173:

   ```sh
   cd frontend && npm install && npm run dev
   ```

Then open <http://localhost:5173>. The landing page should report
`Backend: OK (v0.1.0)` once the backend is up.

### Running tests

```sh
cd backend && ./gradlew test
```
