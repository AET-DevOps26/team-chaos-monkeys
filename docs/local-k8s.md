# Running FoundFlow on local Kubernetes

For day-to-day feature work, `docker compose up` is still the fast path. Use
the local-Kubernetes workflow when you need to test something Kubernetes-
specific: the Helm chart, ServiceMonitor/PrometheusRule resources, ingress
routing, in-cluster Grafana, or anything that will behave differently on AET
than in compose.

Both supported container runtimes ship their own Kubernetes — no extra
distribution is needed. The same Makefile targets work
against both because the Helm chart is distribution-agnostic and the cluster
reads images straight from the host's Docker daemon.

| Runtime         | Enable Kubernetes                                     | Notes                                  |
|-----------------|-------------------------------------------------------|----------------------------------------|
| Docker Desktop (default) | Settings → Kubernetes → ✅ Enable Kubernetes → Apply  | Allocate ≥4 GB RAM in Settings → Resources. |
| OrbStack (alternative)   | `orb config set k8s.enable true && orb stop && orb start` | Lighter on RAM; vanilla upstream k8s. Requires a storage-class override (see below). |

After enabling, set the kubectl context:

```sh
kubectl config use-context docker-desktop   # Docker Desktop
# or
kubectl config use-context orbstack         # OrbStack
```

Confirm `kubectl get nodes` shows one Ready node before continuing.

## Prerequisites

- OrbStack **or** Docker Desktop with its built-in Kubernetes enabled
- `kubectl` >= 1.30
- `helm` >= 3.13

## Quickstart (one command)

```sh
make -C infra/helm kube-quickstart \
  ADMIN_EMAIL=admin@foundflow.local \
  ADMIN_PASSWORD=admin12345 \
  OPENAI_API_KEY=sk-...
```

Writes `values-local.yaml.local` with the three required secrets, bootstraps
the cluster (ingress-nginx + kube-prom-stack + namespace), builds every
service image, and runs `helm upgrade --install`. Prints the entry-point URLs
on success. Re-running is safe — all underlying steps are idempotent. The
manual flow below is for step-by-step control.

The `helm upgrade --install` step uses `--wait --timeout 10m`, so the
quickstart command blocks until every pod is Ready (or it gives up). To watch
progress live in another terminal:

```sh
kubectl -n team-chaos-monkeys get pods -w
```

A healthy install ends with all 18 pods at `1/1 Running` — six `*-db-0`, one
each of `minio-0` and `rabbitmq-*`, nine app deployments, and `grafana-*`.
Spring services may briefly show `0/1 CrashLoopBackOff` while their database
comes up; that's the documented startup race and self-heals within ~60s.


## step by step (setup once)

```sh
make -C infra/helm cluster-bootstrap
```

This installs **ingress-nginx** and **kube-prometheus-stack** (release name
`kps`) into dedicated namespaces, then creates the `team-chaos-monkeys`
namespace — the same name we use on AET, so values diffs stay minimal. It
targets your current kubectl context, so make sure that's pointing at the
local cluster.

## Secrets locally

For local-only secrets (OPENAI_API_KEY, etc.), create a gitignored values file
next to `values-local.yaml`:

```sh
  cat > infra/helm/foundflow/values-local.yaml.local <<'EOF'
  secrets:
    openaiApiKey: sk-...
    devAdminEmail: admin@local.test
    devAdminPassword: changeme
  EOF
```

## Build and deploy (manual)

```sh
make -C infra/helm build          # docker build every service image
make -C infra/helm helm-install   # helm dep update + helm upgrade --install
```

Requires `values-local.yaml.local` (see [Secrets locally](#secrets-locally));
auth-service and genai-service won't start without `devAdminEmail`,
`devAdminPassword`, and `openaiApiKey`.

No image-import step: the local Kubernetes uses the same containerd that the
Docker daemon writes into, so `docker build` is enough. The chart sets
`imageRegistry=foundflow`, `imageTag=dev`, and `imagePullPolicy=IfNotPresent`
(see `infra/helm/foundflow/values-local.yaml`), so kubelet uses the local
image instead of trying to pull from a registry.

First install takes ~3 minutes.

> **Docker Hub TLS timeouts on first install?** If any `*-db-0` pod sits in
> `ImagePullBackOff` (kubelet's pull from Docker Hub hits a TLS handshake
> timeout), pre-pull via your host daemon and restart the stuck pods:
> ```sh
> docker pull postgres:17 && docker pull pgvector/pgvector:pg17
> kubectl -n team-chaos-monkeys delete pod -l app.kubernetes.io/component=database
> ```
> The cluster shares the host's image store, so the next start uses the
> local copy with no network round-trip.

> **Storage class:** the chart pins `global.storageClassName: hostpath`,
> which is the default on Docker Desktop. On OrbStack the default is
> `local-path` — override with `--set global.storageClassName=local-path` on
> the `helm upgrade` line (or add it to your `values-local.yaml.local`).

## Verify

```sh
make -C infra/helm status         # pod / service / ingress overview
make -C infra/helm smoke          # curl the ingress
make -C infra/helm logs           # tail app pods
```

Browser:

- `http://foundflow.localtest.me/` — client
- `http://foundflow.localtest.me/api/actuator/health` — gateway health
- `http://foundflow.localtest.me/grafana/` — Grafana (admin/admin)

`localtest.me` is a public DNS name that resolves all subdomains to
`127.0.0.1`, so it works with the ingress-nginx LoadBalancer service on the
local cluster without any `/etc/hosts` editing.

The chart also brings up **RabbitMQ** (`rabbitmq` Service, ports 5672/15672)
and **MinIO** (`minio` Service, ports 9000/9001) in-namespace. Both are
cluster-internal, not on the ingress. To poke at the MinIO console or the
RabbitMQ management UI:

```sh
kubectl -n team-chaos-monkeys port-forward svc/minio    9001:9001
kubectl -n team-chaos-monkeys port-forward svc/rabbitmq 15672:15672
```

Credentials live in the `foundflow-minio` and `foundflow-rabbitmq` Secrets:
```sh
kubectl -n team-chaos-monkeys get secret foundflow-minio    -o jsonpath='{.data.accessKey}' | base64 -d
kubectl -n team-chaos-monkeys get secret foundflow-rabbitmq -o jsonpath='{.data.username}'  | base64 -d
```

## Iterate on a single service

```sh
# 1. edit the code
# 2. rebuild the image:
docker build -t foundflow/auth-service:dev \
  -f services/auth-service/Dockerfile .
# 3. trigger a rollout:
kubectl -n team-chaos-monkeys rollout restart deployment/auth-service
```

## Inspect metrics

```sh
kubectl -n monitoring port-forward svc/kps-kube-prometheus-stack-prometheus 9090
# open http://localhost:9090/targets
```

You should see all 8 ServiceMonitors as healthy targets and `foundflow-alerts`
listed under Alerts.

> RabbitMQ and MinIO are not currently scraped


The `helm-install` target picks it up automatically when present. The file is
covered by both `.helmignore` and the project `.gitignore`.

## Tearing down

```sh
make -C infra/helm helm-uninstall   # remove just the release (keeps PVCs)
```

To stop the cluster itself: disable Kubernetes in Docker Desktop Settings
(Docker Desktop), or run `orb stop` (OrbStack).

## Notes

For details on the chart layout itself, see `infra/helm/foundflow/README.md`.
