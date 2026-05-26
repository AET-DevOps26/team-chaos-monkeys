# FoundFlow Helm chart

Single umbrella chart that deploys all FoundFlow services (gateway + 6 Spring
microservices + Python GenAI service + React client), six per-service Bitnami
PostgreSQL releasess and an in-namespace Grafana preloaded with the
`Services — RED` dashboard.

Deploys exclusively into the namespace `team-chaos-monkeys`.

## Quick start (local Kubernetes)

The chart runs against the built-in Kubernetes of Docker Desktop (default) or
OrbStack. See `docs/local-k8s.md` for runtime-specific setup; from there:

```sh
make -C infra/helm cluster-bootstrap   # one-time: ingress-nginx + kube-prom-stack + namespace
make -C infra/helm build               # docker build every service image
make -C infra/helm helm-install        # helm dep update + helm upgrade --install
make -C infra/helm smoke               # ingress smoke tests
```

No image-import step is needed: the local Kubernetes shares the host's Docker
daemon image store, and the chart sets `imagePullPolicy=IfNotPresent`.

Then open:

- `http://foundflow.localtest.me/` — client
- `http://foundflow.localtest.me/api/actuator/health` — gateway (401 expected;
  Spring Cloud Gateway routes `/api/**` downstream)
- `http://foundflow.localtest.me/grafana/` — Grafana (`admin` / value of
  `grafana.adminPassword`, which `values-local.yaml` sets to `admin`)

Tear down (keeps PVCs): `make -C infra/helm helm-uninstall`. To stop the
cluster itself, disable Kubernetes in Docker Desktop settings or run
`orb stop` on OrbStack.

## Files

| Path | Purpose |
|------|---------|
| `Chart.yaml` | Declares 6 Bitnami `postgresql` dependencies (one per Spring service). |
| `values.yaml` | Defaults targeting AET (cert-manager, csi-rbd-sc, GHCR images). |
| `values-local.yaml` | Local-cluster overrides: no TLS, `*.localtest.me`, `hostpath` storage (override to `local-path` on OrbStack), `IfNotPresent` pull policy so the shared docker daemon's images are used. |
| `values-aet.yaml` | AET-specific pins. Not applied in CICD-59; consumed by the future CD ticket. |
| `templates/_helpers.tpl` | Labels, image ref, env/DB wiring helpers. |
| `templates/deployments.yaml` | Ranges `.Values.services` → one `Deployment` each. |
| `templates/services.yaml` | Ranges `.Values.services` → one `Service` each. |
| `templates/ingress.yaml` | Single Ingress: `/` → client, `/api` → gateway, `/grafana` → grafana. |
| `templates/secrets.yaml` | `foundflow-app-secrets` (OPENAI_API_KEY, dev admin, JWT key). |
| `templates/servicemonitor.yaml` | One `ServiceMonitor` per scrape-enabled service. |
| `templates/prometheusrule.yaml` | `ServiceDown`, `HighErrorRate`, GenAI alerts. |
| `templates/grafana/*.yaml` | Grafana Deployment + Service + provisioning ConfigMaps + admin Secret. |
| `templates/rabbitmq.yaml` | In-namespace RabbitMQ broker (Deployment + Service + Secret). |
| `templates/minio.yaml` | In-namespace MinIO object store for photos (StatefulSet + Service + Secret). |
| `dashboards/services-red.json` | Symlink to `infra/grafana/dashboards/services-red.json` (the docker-compose Grafana reads the same file). |

## Conventions

- **Kubernetes Service names equal docker-compose service names**
  (`auth-service`, `lost-item-service`, …). The gateway hardcodes URIs like
  `http://auth-service:8081`, so changing a service key here will break in-cluster
  routing — keep them in sync.

- **Bitnami Postgres releases use `fullnameOverride`** so their Service DNS is
  `auth-db`, `lost-item-db`, … exactly like docker-compose. Spring services
  consume `SPRING_DATASOURCE_URL=jdbc:postgresql://<db>:5432/<dbname>` and pull
  the password from the Bitnami-managed Secret (key `password`).

- **All app secrets live in one Secret** (`foundflow-app-secrets`) keyed by
  `.Values.secrets.<name>`. Real values are passed at install time via a
  gitignored `infra/helm/foundflow/values-local.yaml.local` (auto-included
  by the `helm-install` make target when present) or `helm --set`; nothing
  secret is committed.

- **Monitoring resources are labelled `release: <prometheusReleaseLabel>`** so
  the cluster Prometheus Operator picks them up. Default in `values.yaml` is
  `kube-prometheus-stack`; `values-local.yaml` overrides to `kps` (matches the
  `make cluster-bootstrap` install); AET overrides to `rancher-monitoring`.

- **RabbitMQ and MinIO are in-cluster siblings of the apps.** Service names
  match compose (`rabbitmq`, `minio`), so `SPRING_RABBITMQ_HOST=rabbitmq` and
  `PHOTO_STORAGE_ENDPOINT=http://minio:9000` are the same env values used in
  compose. MinIO is **cluster-internal only** — not exposed on the ingress.
  `PHOTO_STORAGE_PUBLIC_ENDPOINT` is also set to `http://minio:9000`, so signed
  URLs are not browser-reachable from the cluster install today; browser-facing
  photo fetches need to go through the gateway, or a follow-up adding a MinIO
  ingress subpath. The shared `MinioPhotoStorage` calls `ensureBucketExists()`
  on startup, so no bucket-bootstrap Job is needed.

## Adding a new service

1. Add a Dockerfile under `services/<name>/`.
2. Add an entry under `services.<name>` in `values.yaml` (and override pieces
   in `values-local.yaml` / `values-aet.yaml` if needed). Pick a unique port.
3. If it needs a database, declare it under `databases.<alias>`, add a Bitnami
   dependency to `Chart.yaml`, and set `dbRef: <alias>` on the service.
4. If it talks to another service, use the bare service name as hostname
   (e.g. `http://auth-service:8081`).
5. `make -C infra/helm build helm-install`.

## Ollama retrofit (deferred)

This chart intentionally omits Ollama. The genai service defaults to
`GENAI_PROVIDER=openai` in cluster (config switch in service code).

To add a local-model option later:

1. Add an `ollama` block to `values.yaml` (image, model, PVC size).
2. Add `templates/ollama.yaml` rendering a Deployment + PVC + Service.
3. In `deployments.yaml`, when `services.genai-service.env.GENAI_PROVIDER=local`,
   inject `OLLAMA_BASE_URL=http://ollama:11434` and `OLLAMA_*_MODEL` env vars.

No service-code changes required — the provider switch already exists.

## Out of scope for CICD-59

- GitHub Actions workflow that runs `helm upgrade --install` against AET on
  merge to `main` (follow-up CICD ticket).
- Image-publish-to-GHCR step in CI (prerequisite for the CD ticket).
- Browser-reachable MinIO host (signed photo URLs from the browser). MinIO
  is cluster-internal in this PR; an ingress subpath or gateway proxy is a
  follow-up.

## Dashboard sync

The canonical dashboard JSON lives at `infra/grafana/dashboards/services-red.json`
(consumed by the docker-compose Grafana). `dashboards/services-red.json` in
this chart is a symlink to it — `helm package` and `helm template` both
follow symlinks and inline the real file, so the docker-compose and Helm
Grafana instances are guaranteed to render the same dashboard.
