# MiniOrch

A container orchestration platform inspired by Kubernetes. Manage Docker containers through a web UI with reconciliation-based control loop, health checks, auto-restart, and live log streaming.

🚧 Under active development.

## How it works

You declare desired state — image, tag, replica count, env, ports, and an optional probe — by `POST`ing a deployment. Spring Boot persists that declaration in Postgres and starts the requested containers. From that moment on, a `@Scheduled` reconciliation loop wakes every ten seconds, asks Docker what is actually running for each deployment, diffs that against the declared desired state, and takes the smallest action that closes the gap: spawn a missing replica, remove a surplus one, restart an exited container with exponential backoff, or run a per-replica health probe (HTTP, TCP, or Docker-state). When a single replica accumulates five failures inside a five-minute sliding window the controller flips it to `CRASHLOOP_BACKOFF` and stops touching it until an operator hits the reset endpoint. Scaling is intent-only — `PATCH /scale` updates the row and writes a `DEPLOYMENT_SCALED` event, and the reconciler converges the container count on its next pass. Same separation of concerns as Kubernetes: the API records what you want, the controller is responsible for making it true.

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

## Health checks and CrashLoopBackOff

Every deployment carries a `probe` config. When omitted it defaults to type `DOCKER`, which trusts the container's reported state. HTTP and TCP probes target the container's bridge IP from the JVM. Probe outcomes feed two pieces of state on each replica: a smoothed `lastProbeResult` (`NOT_PROBED` / `PASSING` / `FAILING`) that flips only when the configured `failureThreshold` is reached, and a sliding `failureWindow` that records every fresh container exit, every failed restart, and every transition into probe-FAILING. When the trimmed window holds five entries inside the last five minutes, the replica goes to `CRASHLOOP_BACKOFF` — the reconciler stops scheduling restarts, stops probing, and writes `CRASHLOOP_BACKOFF_TRIPPED`. An operator un-sticks it with `POST /replicas/{index}/reset`.

```sh
# 1. POST a 1-replica nginx with an HTTP probe at /
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "probedemo",
    "image": "nginx",
    "tag": "1.27-alpine",
    "desiredReplicas": 1,
    "probe": {
      "type": "HTTP",
      "path": "/",
      "port": 80,
      "intervalSeconds": 10,
      "timeoutSeconds": 2,
      "failureThreshold": 3
    }
  }' | tee /tmp/dep.json
ID=$(python3 -c 'import json; print(json.load(open("/tmp/dep.json"))["id"])')

# 2. Simulate an always-crashing workload: kill the container on sight
( while true; do
    docker kill -s SIGKILL \
      $(docker ps -q --filter label=miniorch.deployment-name=probedemo) \
      >/dev/null 2>&1 || true
    sleep 2
  done ) &
KILL_PID=$!

# 3. Watch the controller's reasoning land in the events feed.
#    Within ~50s you will see five failure entries, then
#    CRASHLOOP_BACKOFF_TRIPPED, then nothing more.
for i in 1 2 3 4 5 6; do
  sleep 10
  curl -s "http://localhost:8080/api/v1/deployments/$ID" \
    | python3 -c 'import json,sys; r=json.load(sys.stdin)["replicas"][0]; print(r["status"], "restartCount=" + str(r["restartCount"]))'
done

kill $KILL_PID

# 4. Confirm the controller has stopped touching it
curl -s "http://localhost:8080/api/v1/deployments/$ID/events" \
  | python3 -m json.tool

# 5. Reset and watch it come back
curl -s -X POST "http://localhost:8080/api/v1/deployments/$ID/replicas/0/reset"
sleep 12
docker ps --filter label=miniorch.deployment-name=probedemo \
          --format '{{.Names}} {{.Status}}'

# 6. Cleanup
curl -s -X DELETE "http://localhost:8080/api/v1/deployments/$ID"
```

> **Mac note:** HTTP and TCP probes connect to the container's bridge IP from the JVM running on the host. On Docker Desktop for Mac and Windows that bridge network is not routable from the host, so probes return `FAILING` regardless of container health. The CrashLoopBackOff demo above does not depend on probes — it is driven by container exits, which are observed via Docker inspect and work everywhere. Run the JVM on Linux, or alongside containers on the same Docker network, if you want HTTP/TCP probes to actually exercise the application's `/healthz`. See `docs/adr/0003-health-checks.md`.
