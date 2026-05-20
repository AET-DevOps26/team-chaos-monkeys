# Observability spine — Prometheus + Grafana + alerts — Design

|               |                                                                                  |
| ------------- | -------------------------------------------------------------------------------- |
| **Feature**   | Add the Prometheus/Grafana observability spine that scrapes every FoundFlow service, ships a base dashboard, and triggers at least one alert rule |
| **Issue**     | [#60](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/60) — Configure Prometheus/Grafana monitoring and alerts |
| **Subsystem** | Cross-cutting — adds `infra/`, edits `docker-compose.yml`, fixes `gateway-service` wiring |
| **Author**    | Luca Kollmer (`lgk03`)                                                           |
| **Date**      | 2026-05-20                                                                       |
| **Status**    | Draft — ready for review                                                         |
| **Branch**    | `feat/observability-prometheus-grafana` → `development`                          |

## 1. Context and motivation

Observability is a course-graded requirement (`docs/task-description.md`, `CLAUDE.md`): "Prometheus tracks request count, latency, and error rate at minimum, plus domain metrics ... Grafana dashboards committed as JSON under `infra/grafana/dashboards/`. At least one alert rule ... configured." Spring services must expose `/actuator/prometheus` via Micrometer; the Python service must expose `/metrics`.

**Where we are today.** Instrumentation is mostly there:

| Component | State |
|---|---|
| `auth-service`, `found-item-service`, `lost-item-service`, `matching-service`, `notification-service`, `operations-service` | `/actuator/prometheus` exposed via Micrometer (build.gradle + `management.endpoints.web.exposure.include`) |
| `gateway-service` | Has the actuator starter only — missing `micrometer-registry-prometheus` and the exposure line |
| `genai-service` | `/metrics` via `prometheus_client` (PR [#111](https://github.com/AET-DevOps26/team-chaos-monkeys/pull/111), in review) |
| Prometheus / Grafana / dashboards / alerts | **Not present.** No `infra/` directory. No prometheus or grafana container. No dashboards. No alert rules. |

This PR builds the spine. Domain metrics (`matches/min`, GenAI extraction latency, vector search latency) and advanced observability (#91 — Sentry vs OTel + Loki) are explicitly **downstream**: domain metrics need instrumentation in the owning services (matching, genai), and #91 is gated on this PR being done well per its own body.

## 2. Decision summary

Build a minimal, reproducible observability slice:

- Add `infra/prometheus/{prometheus.yml,alerts.yml}` and `infra/grafana/{provisioning/...,dashboards/*.json}`.
- Add `prometheus` and `grafana` containers to `docker-compose.yml`, **always-on** (no profile).
- Ship one consolidated **Services — RED** dashboard (request rate / error rate / latency) with a `service` templating variable.
- Ship two alert rules: `ServiceDown` and `HighErrorRate`.
- Fix `gateway-service`'s Prometheus wiring (one runtime dep + one properties line) — bundled.

Closes #60. Lands before any work on #91 (advanced observability) or per-service domain metrics.

## 3. Design decisions (with rationale)

### 3.1 Always-on, no `--profile observability`

**Decision.** Prometheus and Grafana come up on every `docker compose up`.

**Rationale.** The course rubric weighs "meaningful observability"; making it discoverable by default — both for the tutor on a cold clone and during a live demo — argues against hiding it behind a flag. Idle resource cost is small (~150 MB for Prometheus tsdb, ~200 MB for Grafana). The course's "three-or-fewer-commands local startup" rule is satisfied either way; making the dev opt in via `--profile` actually moves us in the wrong direction.

**Trade-off accepted.** Adds ~350 MB of memory and ~5 s to a cold compose start. Acceptable.

### 3.2 Directory layout under `infra/`

**Decision.**

```
infra/
  prometheus/
    prometheus.yml
    alerts.yml
  grafana/
    provisioning/
      datasources/datasources.yml
      dashboards/dashboards.yml         # provider config
    dashboards/
      services-red.json                  # the actual dashboard
```

**Rationale.** The task brief names `infra/grafana/dashboards/` for dashboard JSON, so `infra/grafana/` is the fixed root. Mirroring with `infra/prometheus/` keeps the tree obvious. The `provisioning/` subdirectory matches Grafana's expected layout (`/etc/grafana/provisioning/{datasources,dashboards}`), so each subtree mounts 1:1 into the container.

### 3.3 Grafana-as-code (provisioning), not UI-built

**Decision.** All datasources and dashboards are loaded from files in `infra/grafana/provisioning/` and `infra/grafana/dashboards/`.

**Rationale.** A dashboard added through the Grafana UI lives in Grafana's internal sqlite — gone on container rebuild, invisible to grading, undiffable in PRs. Provisioning loads YAML/JSON deterministically on startup. The task brief's "committed as JSON" wording is incompatible with UI-only edits.

**Trade-off accepted.** Editing dashboards becomes "edit JSON in repo, restart Grafana." For a small number of dashboards this is fine; if it ever becomes painful, the Grafana UI export-and-commit workflow is available.

### 3.4 One consolidated dashboard with `service` templating, not eight per-service dashboards

**Decision.** A single dashboard `Services — RED` with a Grafana templating variable `$service` (drop-down listing all scrape targets). Panels query `…{job="$service"}`.

**Rationale.** Eight separate dashboards drift panel-by-panel over time. One dashboard with a service drop-down gives the same per-service view without that drift. Per-domain dashboards (matching throughput, genai latency) are deliberately separate — they ship with the domain-metrics work, not the spine.

### 3.5 Two alert rules, both committed

**Decision.** `ServiceDown` (`up == 0` for 1 min) and `HighErrorRate` (5xx rate > 1% over 5 min, per service).

**Rationale.** The course minimum is one alert rule; two is marginal cost and covers complementary failure modes — "service is gone" (catches crashloops, port misconfigs, broken health probes) and "service is up but broken" (catches regressions and bad deploys). Both reuse Micrometer/Prometheus defaults — `up` is built-in; `http_server_requests_seconds_count{outcome="SERVER_ERROR"}` is what Spring emits — so there is no extra instrumentation to write.

### 3.6 No Alertmanager (alerts visible, not delivered)

**Decision.** Alerts evaluate inside Prometheus and surface on `/alerts` and via the Grafana Prometheus datasource. No Alertmanager container, no Slack/email integration.

**Rationale.** The course requirement is "at least one alert rule **configured**", not delivered. Alertmanager adds a container, a receivers config, and an external integration (Slack webhook or SMTP) — none of which moves the grade. Treated as a deferral, called out in §11.

**Trade-off accepted.** If we need real notifications later (e.g. demo wants to show an alert paging Slack), Alertmanager wires in cleanly behind Prometheus; that's a follow-up ticket.

### 3.7 Scrape targets: in, out, and "later"

**Decision.**

| Target | In this PR? | Notes |
|---|---|---|
| `gateway-service:8080/actuator/prometheus` | Yes | After §3.12 wiring fix |
| `auth-service:8081/actuator/prometheus` | Yes | |
| `lost-item-service:8082/actuator/prometheus` | Yes | |
| `found-item-service:8083/actuator/prometheus` | Yes | |
| `matching-service:8084/actuator/prometheus` | Yes | |
| `notification-service:8085/actuator/prometheus` | Yes | |
| `operations-service:8086/actuator/prometheus` | Yes | |
| `genai-service:8000/metrics` | Yes | Depends on PR #111 merging; until then the target shows `down` and that is fine |
| RabbitMQ | No | Not in compose today (event-driven is planned, not built). Add when RMQ lands. |
| Postgres (per-service DBs) | No | Needs `postgres_exporter` sidecars; that's its own scope. |
| Client (Vite/nginx) | No | No useful runtime metrics; frontend telemetry is a separate concern. |

**Rationale.** I verified RMQ is absent from `docker-compose.yml`; adding a scrape stanza for something that isn't there is dead config. Postgres exporters are real value but cost six new sidecars and a meaningful PR's worth of work — out of scope here.

### 3.8 Service discovery: `static_configs`, not Compose DNS magic

**Decision.** `prometheus.yml` lists each target by Compose-network DNS name in a static block.

**Rationale.** Compose service names are stable; static config is least magic and easiest to reason about. The K8s deployment will use `kubernetes_sd_configs` keyed on the `monitoring: "true"` label per CLAUDE.md — that's a separate Helm chart and lives outside this PR (§10).

### 3.9 Metric-naming divergence (Spring vs Python) — accepted, queried with `or`

**Decision.** Don't try to unify metric names. Dashboard panels that span both Spring services and `genai-service` query with PromQL `or`:

```
rate(http_server_requests_seconds_count[1m])   # Spring / Micrometer
  or
rate(http_requests_total[1m])                   # Python / prometheus_client (per PR #111)
```

**Rationale.** Normalising names requires either a relabel layer in `prometheus.yml` (brittle) or custom instrumentation everywhere (real work). PromQL `or` covers the visualisation case for negligible effort and zero invasive change. The two ecosystems each have idiomatic names and we keep them.

**Trade-off accepted.** A panel reading both metrics is two queries, not one. Tolerable.

### 3.10 Persistent volumes for Prometheus and Grafana

**Decision.** Named volumes `prometheus-data` (tsdb) and `grafana-data` (Grafana's internal sqlite). Mounted to `/prometheus` and `/var/lib/grafana` respectively.

**Rationale.** Without them, every `docker compose down && up` wipes scrape history and any user-modified Grafana state (annotations, saved explores). Adds two lines to `volumes:` and is the standard pattern.

### 3.11 Ports, credentials, and the `.env` story

**Decision.**

- Prometheus on host `:9090` (container `:9090`).
- Grafana on host `:3030` (container `:3000`) — host `:3000` is taken by the React `client` already.
- Grafana admin: `admin` / `admin` defaults, overridable via `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` in `.env.example`.

**Rationale.** Port `:3030` avoids the obvious collision. Defaults of `admin/admin` are fine for pre-prod local dev — Grafana only reads from Prometheus, which exposes the same scraped data anyone on the network can scrape directly. Override path exists because the course brief forbids "hardcoded credentials": defaults are documented and replaceable from `.env`, which is the policy CLAUDE.md describes.

### 3.12 Bundle the gateway-service Prometheus fix

**Decision.** Add `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` to `services/gateway-service/build.gradle` and `management.endpoints.web.exposure.include=health,prometheus,info` to its `application.properties`. Bundled into this PR.

**Rationale.** Two-line fix that completes the parity with the other six Spring services. Spinning it into its own PR is the micro-PR pattern memory tells me to avoid. Without it, the gateway scrape target stays `down` and the consolidated dashboard has a hole.

## 4. `prometheus.yml` shape (illustrative)

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: gateway-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['gateway-service:8080']

  - job_name: auth-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['auth-service:8081']

  # … one block per Spring service (8082–8086)

  - job_name: genai-service
    metrics_path: /metrics
    static_configs:
      - targets: ['genai-service:8000']
```

## 5. `alerts.yml` shape

```yaml
groups:
  - name: foundflow-availability
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.job }} is down"
          description: "Prometheus has not been able to scrape {{ $labels.job }} for 1 minute."

      - alert: HighErrorRate
        expr: |
          sum by (job) (rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[5m]))
            /
          sum by (job) (rate(http_server_requests_seconds_count[5m]))
            > 0.01
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.job }}: server error rate > 1%"
          description: "5xx rate has exceeded 1% over the last 5 minutes."
```

`HighErrorRate` is Spring-only today (Micrometer's `http_server_requests_seconds_count` with `outcome="SERVER_ERROR"`). The `genai-service` equivalent comes once PR #111 lands and we know its exact series name.

## 6. Dashboard: `Services — RED`

Templating: `service` variable populated by `label_values(up, job)`.

Panels (one row per RED metric):

1. **Request rate** — `sum by (job) (rate(http_server_requests_seconds_count{job="$service"}[1m])) or sum by (job) (rate(http_requests_total{job="$service"}[1m]))`
2. **Error rate (5xx fraction)** — Spring outcome-based; Python panel comes when PR #111 settles.
3. **Latency p95** — `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="$service"}[5m])))`
4. **Up / down** — `up{job="$service"}` as a stat panel.

Saved as `infra/grafana/dashboards/services-red.json` with `editable: false` so accidental UI edits don't drift from the committed JSON.

## 7. Files added / changed

```
infra/
  prometheus/
    prometheus.yml                              [new]
    alerts.yml                                  [new]
  grafana/
    provisioning/
      datasources/datasources.yml               [new]
      dashboards/dashboards.yml                 [new]
    dashboards/
      services-red.json                          [new]
docker-compose.yml                              [edit] — add prometheus, grafana, volumes
.env.example                                    [edit] — add GRAFANA_ADMIN_USER/PASSWORD
services/gateway-service/build.gradle           [edit] — add micrometer-registry-prometheus
services/gateway-service/src/main/resources/application.properties   [edit] — exposure
README.md                                       [edit] — link to Grafana :3030, Prometheus :9090
```

## 8. Validation (local)

A reviewer (or grader) should be able to:

1. `cp .env.example .env && docker compose up --build`.
2. Open `http://localhost:9090/targets` — all 8 scrape jobs `UP`.
3. Open `http://localhost:3030` — log in `admin`/`admin`, find the **Services — RED** dashboard provisioned, drop-down lists every service.
4. Open `http://localhost:9090/alerts` — both `ServiceDown` and `HighErrorRate` listed in `inactive` state.
5. Stop one service (`docker compose stop auth-service`) — within ~1 min, `ServiceDown` flips to `firing` for `job="auth-service"`.

This is the PR's smoke-test; a `Test plan` checklist on the PR maps 1:1 to these steps.

## 9. K8s deployment is out of scope here

The same Prometheus + Grafana + dashboards will need to deploy under Helm to the Rancher and Azure clusters. The K8s scrape config will switch to `kubernetes_sd_configs` keyed on the `monitoring: "true"` pod label (per CLAUDE.md), and the Grafana dashboards re-use the same JSON via a ConfigMap.

That work is a separate ticket — likely sub-issue of #59 (Helm deployment) or its own — and depends on Helm charts existing, which they do not yet. Adding K8s deployment here would inflate this PR for value that lands only when the Helm tree starts.

## 10. Follow-up tickets to file (not in this PR)

- **Domain metrics — matching service.** Custom Micrometer counters/timers for matches/min, vector-search latency, attribute-score latency. New ticket, owner = Johannes.
- **Domain metrics — genai-service.** Custom histograms for extraction latency, embedding latency, verification latency by `verdict`. New ticket, owner = Luca (or extend #54).
- **Postgres exporters.** Per-DB `postgres_exporter` sidecars + scrape stanzas.
- **RabbitMQ scraping.** Lands when RMQ lands.
- **K8s observability (Helm).** Mirror this slice into the Helm chart.
- **Alertmanager + delivery.** Wire alerts to Slack/email if/when the demo wants visible paging.

## 11. Out of scope

- The advanced-observability decision (#91 — Sentry vs OTel + Loki). This PR builds the required spine; #91 is bonus and downstream.
- Tracing (`/actuator/httptrace`, OTel tracing, Tempo) — part of #91.
- Log aggregation (Loki, fluent-bit) — part of #91.
- Frontend telemetry — separate concern, frontend subsystem.
- Per-DB Postgres metrics — separate ticket per §10.
- The genai-service's exact metric names — defined by PR #111. This PR's dashboard tolerates whatever names land there (queries fall back via `or`).
