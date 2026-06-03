# Problem Statement

Hospitality and event venues handle a constant stream of lost-and-found cases.
The process is often manual: staff log found items in spreadsheets or paper
notebooks, guests describe losses by email or phone, and matching depends on
human memory. The result is low return rates, frustrated guests, and avoidable
staff effort.

FoundFlow targets the core gap: the information needed to match a lost report
to a found item exists, but it is mostly unstructured human language. The app
digitises both intake paths, extracts structured attributes with GenAI, and uses
semantic matching to surface likely candidate pairs.

## Main Functionality

1. Found-item intake: staff register a found item with a photo and description.
2. Guest lost-item reporting: guests submit free-text lost reports through a
   public form, optionally with a photo.
3. Matching: the matching service combines structured attributes and vector
   similarity to create candidate matches.
4. Notification and claim: staff create public match links; guests confirm or
   reject candidates and can schedule pickup.
5. Operations: venue and KPI endpoints expose the operational state.

## Scope

In scope:

- React frontend
- Spring Boot microservices
- Python GenAI microservice
- PostgreSQL per service
- MinIO/Azure-compatible photo storage abstraction
- RabbitMQ domain events
- Docker Compose local runtime
- Helm/Kubernetes deployment
- Prometheus/Grafana observability
- Unit, integration, frontend, and GenAI tests

Out of scope for this iteration:

- Payment or restitution flows
- Courier/shipping integration
- Native mobile apps
- Billing
- SSO
- Fully persisted notification blueprints
