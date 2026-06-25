# Local Kubernetes Runtime

Use Docker Compose for day-to-day feature work. Use local Kubernetes when you
need to test Helm charts, ingress routing, ServiceMonitor/PrometheusRule
resources, in-cluster Grafana, or behavior that differs from compose.

## Prerequisites

- Docker Desktop with Kubernetes enabled, or OrbStack with Kubernetes enabled
- `kubectl` 1.30 or newer
- `helm` 3.13 or newer

Set the context:

```sh
kubectl config use-context docker-desktop
# or
kubectl config use-context orbstack
```

Docker Desktop uses `hostpath` as the default storage class. OrbStack commonly
uses `local-path`; if pods stay pending because of PVC binding, set
`global.storageClassName=local-path` in `values-local.yaml.local` or on the Helm
command line.

## Quickstart

```sh
make -C infra/helm kube-quickstart \
  ADMIN_EMAIL=admin@foundflow.local \
  ADMIN_PASSWORD=admin12345 \
  OPENAI_API_KEY=sk-...
```

The target writes a local values file, bootstraps ingress and monitoring,
builds service images, and runs `helm upgrade --install`.

Watch rollout:

```sh
kubectl -n team-chaos-monkeys get pods -w
```

## Manual Flow

```sh
make -C infra/helm cluster-bootstrap
make -C infra/helm build
make -C infra/helm helm-install
```

Local-only secrets belong in:

```text
infra/helm/foundflow/values-local.yaml.local
```

That file is gitignored.

## Verify

```sh
make -C infra/helm status
make -C infra/helm smoke
make -C infra/helm logs
```

Browser URLs:

- `http://foundflow.localtest.me/`
- `http://foundflow.localtest.me/api/actuator/health`
- `http://foundflow.localtest.me/grafana/`

`localtest.me` resolves to `127.0.0.1`, so no hosts-file edit is needed.

If database pods fail on first install with `ImagePullBackOff` because Docker
Hub pulls time out, pre-pull the images through the host daemon and restart the
database pods:

```sh
docker pull postgres:17
docker pull pgvector/pgvector:pg17
kubectl -n team-chaos-monkeys delete pod -l app.kubernetes.io/component=database
```

If application pods fail with `ImagePullBackOff` for images such as
`foundflow/auth-service:dev`, Kubernetes is trying to pull `docker.io/foundflow/*`
instead of using a local image. Some local Kubernetes runtimes do not share the
host Docker image store with the cluster runtime. In that case:

- verify every chart image was built locally (`docker images "foundflow/*"`),
  including `pickup-service` and `public-report-client`;
- use a runtime that can see the host images, import the images into the cluster
  runtime, or push them to a reachable registry and override
  `global.imageRegistry`;
- after fixing image visibility, restart the affected deployments with
  `kubectl -n team-chaos-monkeys rollout restart deployment/<name>`.

## Internal Tools

RabbitMQ and MinIO are cluster-internal. Port-forward them when needed:

```sh
kubectl -n team-chaos-monkeys port-forward svc/minio 9001:9001
kubectl -n team-chaos-monkeys port-forward svc/rabbitmq 15672:15672
```

## Iterate on One Service

```sh
docker build -t foundflow/auth-service:dev \
  -f services/auth-service/Dockerfile .

kubectl -n team-chaos-monkeys rollout restart deployment/auth-service
```

## Metrics

```sh
kubectl -n monitoring port-forward svc/kps-kube-prometheus-stack-prometheus 9090
```

Open `http://localhost:9090/targets` and verify FoundFlow targets are healthy.

RabbitMQ and MinIO are currently available for application workflows but are
not scraped as Prometheus targets in the baseline setup.

## Teardown

```sh
make -C infra/helm helm-uninstall
```

This removes the release but keeps PVCs.
