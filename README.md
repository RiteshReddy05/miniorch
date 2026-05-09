# MiniOrch

A container orchestration platform inspired by Kubernetes. Manage Docker containers through a web UI with reconciliation-based control loop, health checks, auto-restart, and live log streaming.

🚧 Under active development.

## How it works

You declare desired state — image, tag, replica count, env, ports — by `POST`ing a deployment. Spring Boot persists that declaration in Postgres and starts the requested containers. From that moment on, a `@Scheduled` reconciliation loop wakes every ten seconds, asks Docker what is actually running for each deployment, diffs that against the declared desired state, and takes the smallest action that closes the gap: spawn a missing replica, remove a surplus one, or restart an exited container with exponential backoff. Scaling is intent-only — `PATCH /scale` updates the row and writes a `DEPLOYMENT_SCALED` event, and the reconciler converges the container count on its next pass. Same separation of concerns as Kubernetes: the API records what you want, the controller is responsible for making it true.

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

The Docker integration test (`DockerServiceIT`) is tagged `docker` and excluded by default. To run it against your local daemon:

```sh
cd backend && ./gradlew test -PrunDockerIT
```

## Self-healing demo

With Postgres and the backend running, create a 2-replica nginx deployment and watch the reconciler put a killed container back:

```sh
# 1. Declare desired state: 2 replicas of nginx 1.27-alpine
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "selfhealdemo",
    "image": "nginx",
    "tag": "1.27-alpine",
    "desiredReplicas": 2
  }' | tee /tmp/dep.json

ID=$(python3 -c 'import json; print(json.load(open("/tmp/dep.json"))["id"])')

# 2. Confirm both containers are running
docker ps --filter label=miniorch.deployment-name=selfhealdemo \
          --format '{{.Names}} {{.Status}}'

# 3. Kill one out from under the controller
docker kill miniorch-selfhealdemo-1

# 4. Wait one reconciliation tick (~10s) and look again
sleep 12
docker ps --filter label=miniorch.deployment-name=selfhealdemo \
          --format '{{.Names}} {{.Status}}'

# 5. Read the controller's decision trail
curl -s "http://localhost:8080/api/v1/deployments/$ID/events" \
  | python3 -m json.tool
```

You will see a fresh `miniorch-selfhealdemo-1` container, and the events feed shows the reconciler's reasoning — `REPLICA_RESTART_SCHEDULED`, `REPLICA_RESTART_ATTEMPTED`, and a `DEPLOYMENT_HEALTHY` transition once the new container is up.

Scaling works the same way — record intent, let the loop converge:

```sh
curl -s -X PATCH "http://localhost:8080/api/v1/deployments/$ID/scale" \
  -H 'Content-Type: application/json' \
  -d '{"desiredReplicas": 4}'

sleep 12
docker ps --filter label=miniorch.deployment-name=selfhealdemo \
          --format '{{.Names}} {{.Status}}'
```

Cleanup:

```sh
curl -s -X DELETE "http://localhost:8080/api/v1/deployments/$ID"
```
