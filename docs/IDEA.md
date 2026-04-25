# MiniOrch — What and Why

## What it is

MiniOrch is a small container orchestration platform built on top of the Docker Engine. The user declares desired state through a REST API or web UI ("I want 3 replicas of `nginx:1.27`"), and a reconciliation loop continuously observes the actual state of containers, detects drift, and converges by creating, restarting, or removing containers until reality matches the declaration.

It is the same control-plane pattern that Kubernetes uses, expressed in roughly two thousand lines of Java instead of two million lines of Go.

## Why build it

### Why control-plane, not CRUD

Most portfolio projects are CRUD apps with authentication and a database. They demonstrate that the author can wire a form to a table. They do not demonstrate any opinion about systems.

Reconciliation is interesting because it forces a different mental model:

- Imperative APIs say *do this thing now*. Reconciliation APIs say *this is what the world should look like; figure it out*.
- The system has to be tolerant of partial failure. A container can crash mid-loop; the next loop pass should still converge.
- State lives in two places — the declared spec in the database and the observed reality from Docker — and the controller is the bridge between them. That is a non-trivial design problem at any scale.

For a Java backend role, working through this pattern produces concrete answers to questions like *how do you handle a long-running background task that needs to be both reliable and observable*, which is materially harder than *show me an endpoint that returns a list*.

### Why Docker Engine, not a real cluster

The point of the project is the control loop, not the runtime. The Docker Engine API gives a stable, well-documented surface for creating, inspecting, restarting, and tailing containers, on a single machine, with no infrastructure beyond the laptop the project is built on. Targeting a real Kubernetes cluster would mean spending the budget on cluster plumbing instead of on the loop, the reconciliation logic, and the developer experience.

## Comparison to Kubernetes

| Concern | Kubernetes | MiniOrch |
|---|---|---|
| Desired-state model | Custom Resources (Deployment, ReplicaSet, Pod) | `Deployment` row in Postgres |
| Reconciliation | Many controllers, each watching one resource type | Single scheduled loop, every 10s |
| Runtime | containerd / CRI on a multi-node cluster | Docker Engine on one host |
| Networking | CNI, kube-proxy, Services | Host networking and published ports |
| Scheduling | kube-scheduler placing Pods on Nodes | Trivial — every container runs on the local Docker daemon |
| Health checks | Liveness, readiness, startup probes | HTTP, TCP, and Docker-native checks |
| Restart policy | Backoff with CrashLoopBackOff | Exponential backoff (1s → 60s) with CrashLoopBackOff after 5 failures in 5 min |
| Audit trail | etcd events + audit logs | Events table in Postgres |
| Auth | RBAC via API server | JWT bearer tokens |

## Honest framing

MiniOrch is a **learning project**, not a Kubernetes alternative.

- It runs on a single machine. There is no scheduler, no networking layer, no multi-tenancy story, no real failure isolation between containers.
- It does not aim to handle thousands of workloads. The reconciliation loop is sequential and the loop interval is fixed at 10 seconds.
- The value of the project is in working through the reconciliation pattern, the controller code, the Docker integration, and the operational surface (events, logs, metrics, restart policy) end-to-end. It is a portfolio piece for backend engineering interviews, not infrastructure to put real workloads on.
