# FoundFlow Helm chart

Current umbrella chart for the cluster deployment. It deploys the gateway,
seven Spring backend services (`auth`, `lost-item`, `found-item`, `matching`,
`notification`, `operations`, `pickup`), the Python GenAI service, the React
client, the public report client, seven per-service PostgreSQL StatefulSets,
RabbitMQ, MinIO, and an in-namespace
Grafana preloaded with the `Services — RED` dashboard.

Deploys exclusively into the namespace `team-chaos-monkeys`.

## Quick start (local Kubernetes)

The chart runs against the built-in Kubernetes of Docker Desktop (default) or
OrbStack. See [local Kubernetes runtime](../../../docs/deployment/local-kubernetes.md) for runtime-specific setup. One-command path:

```sh
make -C infra/helm kube-quickstart \
  ADMIN_EMAIL=admin@foundflow.local \
  ADMIN_PASSWORD=admin12345 \
  OPENAI_API_KEY=sk-...
```

Step-by-step (manual) path:

```sh
make -C infra/helm cluster-bootstrap   # one-time: ingress-nginx + kube-prom-stack + namespace
make -C infra/helm build               # docker build every service image
make -C infra/helm helm-install        # helm dep update + helm upgrade --install
make -C infra/helm smoke               # ingress smoke tests
```

The local values use `foundflow/<service>:dev` images and set
`imagePullPolicy=IfNotPresent`. Runtimes that share the host Docker image store
can use the locally built images directly. If the cluster runtime instead tries
to pull `docker.io/foundflow/*` and pods enter `ImagePullBackOff`, import the
images into the cluster runtime or push them to a reachable registry and override
`global.imageRegistry`.

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
| `Chart.yaml` | Declares chart metadata; service and database resources are rendered from `values.yaml`. |
| `values.yaml` | Defaults targeting AET (cert-manager, csi-rbd-sc, GHCR images). |
| `values-local.yaml` | Local-cluster overrides: no TLS, `*.localtest.me`, `hostpath` storage (override to `local-path` on OrbStack), `IfNotPresent` pull policy for locally available images. Some runtimes still need image import or a reachable registry. |
| `values-aet.yaml` | AET-specific pins consumed by `.github/workflows/aet-helm-deploy.yml`. |
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

- **Per-service Postgres resources keep compose-compatible DNS names.** The
  chart renders one StatefulSet, Service, PVC, and Secret per entry in
  `.Values.databases`, so Spring services consume
  `SPRING_DATASOURCE_URL=jdbc:postgresql://<db>:5432/<dbname>` and pull the
  password from the matching database Secret (key `password`).

- **All app secrets live in one Secret** (`foundflow-app-secrets`) keyed by
  `.Values.secrets.<name>`. Real values are passed at install time via a
  gitignored `infra/helm/foundflow/values-local.yaml.local` (auto-included
  by the `helm-install` make target when present) or `helm --set`; nothing
  secret is committed.

  Recognised keys (all optional unless flagged):
  | values key             | env var consumed by                      | purpose |
  |------------------------|------------------------------------------|---------|
  | `openaiApiKey`         | `OPENAI_API_KEY` (genai-service)         | OpenAI provider key when `GENAI_PROVIDER=openai`. |
  | `devAdminEmail`        | `DEV_ADMIN_EMAIL` (auth-service)         | Bootstrap admin email seeded on first start. |
  | `devAdminPassword`     | `DEV_ADMIN_PASSWORD` (auth-service)      | Bootstrap admin password. |
  | `jwtRsaPrivateKey`     | `JWT_RSA_PRIVATE_KEY` (auth-service)     | Override the auto-generated JWT signing key. |
  | `internalServiceToken` | `INTERNAL_SERVICE_TOKEN` (found-item, matching) | Shared token for matching-service calls to found-item internal endpoints. Required. |
  | `magicLinkSecret`      | `MAGIC_LINK_SECRET` (matching, pickup, notification) | HMAC secret for public match/pickup magic-link tokens. Falls back to the dev default when empty. |
  | `brevoSmtpUsername`    | `SPRING_MAIL_USERNAME` (notification-service) | Brevo SMTP login (the verified Foundflow Gmail). |
  | `brevoSmtpPassword`    | `SPRING_MAIL_PASSWORD` (notification-service) | Brevo SMTP password (issued in the Brevo dashboard). |
  | `brevoMailFromAddress` | `FOUNDFLOW_MAIL_FROM` (notification-service)  | From: header for outbound email. Falls back to `foundflow.notifications.from-address`. |

  Without `brevoSmtp*` set the notification-service consumer still persists
  every notifications row (URL in `body`), but each SMTP attempt fails
  authentication and the message is dropped after the bounded retry —
  visible on the `notifications_send_failures_total` Prometheus counter.

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
3. If it needs a database, add an entry under `.Values.databases` and set
   `dbRef: <alias>` on the service.
4. If it talks to another service, use the bare service name as hostname
   (e.g. `http://auth-service:8081`).
5. `make -C infra/helm build helm-install`.

## Deploy to AET via GitHub Actions

The AET deployment workflow is `.github/workflows/aet-helm-deploy.yml`. It
builds and pushes all application images to GHCR with one immutable tag, then
runs:

```sh
helm upgrade --install foundflow ./infra/helm/foundflow \
  -n team-chaos-monkeys \
  -f infra/helm/foundflow/values-aet.yaml \
  -f <generated-secret-values-file> \
  --set-string global.imageTag=<commit-sha> \
  --wait --atomic --timeout 15m
```

Triggers:

- automatic: every push to `main`
- manual: Actions -> `Deploy Helm chart to AET` -> `Run workflow`

Required repository secrets:

| Secret | Purpose |
|--------|---------|
| `AET_KUBECONFIG_B64` | Base64-encoded kubeconfig for the AET cluster. |
| `OPENAI_API_KEY` | GenAI OpenAI provider key. |
| `DEV_ADMIN_EMAIL` | Bootstrap admin email. |
| `DEV_ADMIN_PASSWORD` | Bootstrap admin password. |
| `INTERNAL_SERVICE_TOKEN` | Shared internal service token for matching/found-item calls. |
| `MAGIC_LINK_SECRET` | HMAC secret for public match and pickup links. |
| `BREVO_SMTP_USERNAME` | Brevo SMTP username. |
| `BREVO_SMTP_PASSWORD` | Brevo SMTP password. |
| `BREVO_MAIL_FROM` | Sender address for outbound mail. |

Optional repository secrets:

| Secret | Purpose |
|--------|---------|
| `JWT_RSA_PRIVATE_KEY` | Stable JWT signing key for auth-service. |
| `GENAI_INTERNAL_TOKEN` | Optional token sent to genai-service by Spring callers. |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password. When unset, falls back to `admin`. Owning it here keeps the `foundflow-grafana` Secret from drifting: Grafana's DB lives in an `emptyDir` and re-seeds the password from this Secret on every pod restart. |

To create `AET_KUBECONFIG_B64` from a working local kubeconfig:

```sh
base64 -w 0 ~/.kube/config
```

On macOS, use:

```sh
base64 < ~/.kube/config | tr -d '\n'
```

## Verify an AET deployment

In GitHub Actions, the workflow should show:

- successful `Build + push images`
- successful `Helm lint`
- successful `helm upgrade --install`
- successful public smoke checks for `/` and `/api/actuator/health`

From a local machine with AET cluster access:

```sh
kubectl -n team-chaos-monkeys get pods,svc,ingress
kubectl -n team-chaos-monkeys rollout status deployment/gateway-service
kubectl -n team-chaos-monkeys rollout status deployment/client
helm -n team-chaos-monkeys status foundflow
helm -n team-chaos-monkeys history foundflow
```

From any machine with internet access:

```sh
curl -I https://team-chaos-monkeys.stud.k8s.aet.cit.tum.de/
curl -I https://team-chaos-monkeys.stud.k8s.aet.cit.tum.de/api/actuator/health
```

Expected results: the client returns `200`; the gateway health endpoint through
the public ingress returns `401` because the gateway security chain is active.

## Ollama retrofit (deferred)

This chart intentionally omits Ollama. The genai service defaults to
`GENAI_PROVIDER=openai` in cluster (config switch in service code).

To add a local-model option later:

1. Add an `ollama` block to `values.yaml` (image, model, PVC size).
2. Add `templates/ollama.yaml` rendering a Deployment + PVC + Service.
3. In `deployments.yaml`, when `services.genai-service.env.GENAI_PROVIDER=local`,
   inject `OLLAMA_BASE_URL=http://ollama:11434` and `OLLAMA_*_MODEL` env vars.

No service-code changes required — the provider switch already exists.

## Current limitations

- Browser-reachable MinIO host (signed photo URLs from the browser). MinIO is
  cluster-internal in this chart; an ingress subpath or gateway proxy is a
  follow-up.

## Dashboard sync

The canonical dashboard JSON lives at `infra/grafana/dashboards/services-red.json`
(consumed by the docker-compose Grafana). `dashboards/services-red.json` in
this chart is a symlink to it — `helm package` and `helm template` both
follow symlinks and inline the real file, so the docker-compose and Helm
Grafana instances are guaranteed to render the same dashboard.
