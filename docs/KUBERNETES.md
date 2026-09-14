# Kubernetes architecture for CollabMind

## What Kubernetes is

Kubernetes (often shortened to **K8s**) is a control system for containers. You describe the state you want in YAML—for example, “run three realtime gateways”—and Kubernetes continually tries to keep that state true. If a container crashes, Kubernetes replaces it. If traffic grows, it can add replicas. If a release is unhealthy, it stops sending traffic to it.

Kubernetes is not the application, a programming language, or a replacement for PostgreSQL, Redis, or Kafka. It is the platform that starts, connects, checks, scales, and updates the CollabMind containers.

## CollabMind request flow

```text
Browser
  |
  | HTTPS / secure WebSocket
  v
Ingress controller (public entry point and TLS)
  |-- /                         -> web
  |-- /api/auth, /api/users     -> identity-service -> identity PostgreSQL
  |-- /api/conversations        -> chat-core        -> chat PostgreSQL + Redis
  |-- /ws/chat                  -> realtime-gateway -> Redis + Kafka
  `-- /mcp                      -> tool-mcp-server   -> Kapruka MCP
                                                   
Kafka -> ai-orchestrator -> OpenAI + tool-mcp-server -> AI PostgreSQL
Redis -> presence, cross-pod realtime fanout, rate limits, duplicate guards
```

The browser sees one domain, such as `https://chat.example.com`. The Ingress routes each path to the correct private Kubernetes Service. Backend pods are not directly public.

## The Kubernetes words used here

| Object | Meaning in this project |
|---|---|
| Cluster | The complete Kubernetes environment. It contains worker machines called nodes. |
| Namespace | A logical boundary named `collabmind` that groups this application's objects. |
| Pod | One running instance of one CollabMind container. Pods are replaceable and should not store permanent data locally. |
| Deployment | Declares the image, configuration, health checks, and desired number of pods for a stateless service. |
| Replica | A copy of a pod. More replicas provide capacity and allow upgrades without total downtime. |
| Service | A stable internal DNS name and virtual address in front of replaceable pods, such as `http://chat-core:8081`. |
| Ingress | The public HTTPS router. It sends URL paths to internal Services and supports the WebSocket route. |
| ConfigMap | Non-secret configuration, such as internal service URLs and the OpenAI model name. |
| Secret | Passwords and tokens. The real Secret is deliberately not committed. |
| Probe | A health request. Readiness controls whether a pod receives traffic; liveness restarts a stuck pod. |
| HPA | Horizontal Pod Autoscaler. It adds or removes realtime/AI pods based on CPU utilization. |
| PDB | Pod Disruption Budget. It keeps at least two realtime pods available during voluntary maintenance. |
| NetworkPolicy | An internal firewall. The base denies unsolicited ingress and then allows the public router and application-to-application traffic. |
| Kustomize | Kubernetes' built-in manifest composition tool. `kustomization.yaml` assembles the files into one deployable result. |

## Why every service has multiple pods

`web`, `identity-service`, `chat-core`, `ai-orchestrator`, and `tool-mcp-server` start with two replicas. `realtime-gateway` starts with three because every active browser holds a long-lived WebSocket there.

Realtime state cannot live only inside one gateway pod: the next browser may connect to another pod, and a pod can disappear at any time. CollabMind therefore uses Redis Pub/Sub and shared presence for fast cross-pod fanout, while Kafka carries durable events for asynchronous AI processing and replay.

## Why PostgreSQL, Redis, and Kafka are not deployed by this base

They are stateful systems. A single YAML pod is easy to demonstrate but is not production architecture: backups, replication, disk failure, upgrades, failover, and monitoring all need ownership. The production base expects managed PostgreSQL, managed Redis, and managed Kafka—or mature operators managed by a platform team.

The addresses are configured in `configmap.yaml`; credentials and database URLs come from `collabmind-secrets`. Nothing stops you using local infrastructure while learning, but it should not be presented as highly available production storage.

## Files

```text
Dockerfile.web                         production web image
deploy/nginx/default.conf              SPA routing and health endpoint
deploy/kubernetes/base/
  namespace.yaml                       application boundary
  service-account.yaml                 identity with API token mounting disabled
  configmap.yaml                       non-secret runtime configuration
  workloads.yaml                       six Deployments and health/resource rules
  services.yaml                        private stable service discovery
  ingress.yaml                         public HTTPS/WebSocket path routing
  availability.yaml                    autoscaling and realtime disruption protection
  network-policy.yaml                  internal ingress firewall
  kustomization.yaml                   manifest entry point
deploy/kubernetes/secrets.example.env  names of required secret values only
```

## Before the first deployment

You need:

1. A Kubernetes cluster and `kubectl` configured for it.
2. An NGINX Ingress Controller. An Ingress object does nothing until a controller implements it.
3. Metrics Server for the CPU-based HPAs.
4. A TLS certificate stored as the `collabmind-tls` Secret (or cert-manager configured to create it).
5. Three PostgreSQL databases, Redis, and Kafka reachable from cluster pods.
6. Six container images pushed to a registry the cluster can read.

Change `chat.example.com`, all `*.example.internal` addresses, and image names/tags before applying anything.

## Build and publish images

Use an immutable release tag rather than `latest`:

```powershell
$tag='0.1.0'
docker build -f Dockerfile.web -t ghcr.io/movindujay/collabmind-web:$tag .
docker build -f Dockerfile.service --build-arg SERVICE_PATH=services/identity-service --build-arg JAVA_VERSION=23 -t ghcr.io/movindujay/collabmind-identity-service:$tag .
# Repeat the service build for chat-core, realtime-gateway, ai-orchestrator, and tool-mcp-server.
docker push ghcr.io/movindujay/collabmind-web:$tag
```

This build step needs a container builder somewhere, such as GitHub Actions or a CI runner. You do not need to install Docker on this workstation merely to keep developing the application locally.

## Create secrets safely

Do not edit real values into Git-tracked YAML. Copy `secrets.example.env` to a secure location outside the repository, replace every value, then create the Secret:

```powershell
kubectl create namespace collabmind --dry-run=client -o yaml | kubectl apply -f -
kubectl -n collabmind create secret generic collabmind-secrets --from-env-file='D:\secure\collabmind-production.env'
kubectl -n collabmind create secret tls collabmind-tls --cert='D:\secure\tls.crt' --key='D:\secure\tls.key'
```

Kubernetes Secrets are not automatically a complete secret-management solution. Production clusters should enable encryption at rest and least-privilege RBAC, or integrate an external secret store.

## Validate and deploy

Render locally without changing a cluster:

```powershell
kubectl kustomize deploy\kubernetes\base
```

Ask the cluster to validate the rendered resources:

```powershell
kubectl apply --dry-run=server -k deploy\kubernetes\base
```

Deploy and watch the rollout:

```powershell
kubectl apply -k deploy\kubernetes\base
kubectl -n collabmind get pods,services,ingress,hpa
kubectl -n collabmind rollout status deployment/realtime-gateway
```

Useful diagnosis commands:

```powershell
kubectl -n collabmind describe pod <pod-name>
kubectl -n collabmind logs deployment/realtime-gateway --tail=200
kubectl -n collabmind get events --sort-by=.lastTimestamp
```

## How a release works

1. CI tests the code and builds a uniquely tagged image.
2. The image tag in the manifests is updated.
3. `kubectl apply` changes the Deployment's desired state.
4. Kubernetes creates new pods gradually.
5. A new pod receives traffic only after its readiness probe passes.
6. Old pods are removed after replacements are ready.
7. If readiness never succeeds, the rollout stalls instead of replacing every healthy pod.

Database migrations need extra care. Flyway currently runs during application startup; for heavily used production systems, move migrations to a single pre-deployment Job so multiple replicas cannot race and a failed migration cannot block every new pod.

## What this does not magically solve

- Application bugs, incorrect database migrations, and bad AI responses still require tests and observability.
- HPA CPU scaling needs Metrics Server. WebSocket connection count and Kafka consumer lag would be better custom scaling signals later.
- The NetworkPolicies work only when the cluster's network plugin enforces them.
- Backups and disaster recovery belong to the PostgreSQL/Redis/Kafka provider configuration.
- Logs currently go to container stdout. A production cluster should collect them centrally and add metrics/tracing alerts.
