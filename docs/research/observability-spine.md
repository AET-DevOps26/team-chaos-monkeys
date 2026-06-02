# Observability Spine

Status: implemented in compose and Helm-oriented infrastructure.

FoundFlow uses Prometheus and Grafana as the baseline observability stack. The
goal is to make service health, request rate, errors, and latency visible by
default in local and Kubernetes-oriented deployments.

## Components

| Component | Location |
| --- | --- |
| Prometheus compose config | `infra/prometheus/prometheus.yml` |
| Prometheus alert rules | `infra/prometheus/alerts.yml` |
| Grafana provisioning | `infra/grafana/provisioning` |
| Grafana dashboards | `infra/grafana/dashboards` |
| Helm dashboard config | `infra/helm/foundflow/dashboards` |
| Helm PrometheusRule | `infra/helm/foundflow/templates/prometheusrule.yaml` |
| Helm ServiceMonitor | `infra/helm/foundflow/templates/servicemonitor.yaml` |

## Metrics Sources

- Spring services expose `/actuator/prometheus` via Micrometer.
- Gateway also exposes actuator metrics.
- GenAI exposes `/metrics` through `prometheus_client`.

The core dashboard shape follows RED metrics:

- Request rate
- Error rate
- Duration/latency
- Up/down status

## Alerts

The baseline alerts cover:

- Service unavailable/down
- High server error rate

Alert evaluation happens in Prometheus. Alert delivery through Alertmanager is
not part of the current baseline.

## Runtime Defaults

In Docker Compose:

- Prometheus runs on `localhost:9090`.
- Grafana runs on `localhost:3030`.
- Grafana credentials default to environment-configurable `admin` / `admin`.

## Follow-Ups

Potential extensions:

- Per-service domain metrics such as matches per minute or verification latency.
- RabbitMQ and PostgreSQL exporter coverage.
- MinIO exporter coverage if photo-storage behavior becomes demo-critical.
- Alertmanager delivery.
- Distributed tracing and log aggregation.
