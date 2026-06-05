# Architecture Risks

This document tracks recurring architecture and delivery risks that are useful
for design reviews, tutor checkpoints, and final presentation preparation.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Local LLM quality drift | Prompts that work with OpenAI can return malformed or lower-quality output with local Ollama models. | Keep schema validation strict, use fake-provider tests in CI, and run gated real-provider/golden tests when changing prompts. |
| Kubernetes and observability ramp-up | Deployment or monitoring work can become a bottleneck if knowledge stays with one person. | Keep Helm simple, document local Kubernetes flows, and share operational debugging work. |
| API contract drift | Multiple Spring services and the React client depend on generated API clients and service Swagger/OpenAPI output. | Treat API changes as contract changes, regenerate clients, and keep gateway/security routes aligned. |
| Event contract drift | RabbitMQ decouples services but can hide breaking payload changes. | Keep event payloads in `shared/domain-events`, version routing keys, and add consumer tests for core event flows. |
| Demo data gap | A deployed system without realistic data is hard to demonstrate and can look broken. | Prepare repeatable demo data or seed flows for lost reports, found items, and candidate matches. |
| Photo storage drift | MinIO/local/Azure behavior can diverge if services implement storage logic independently. | Keep all photo handling behind `shared/photo-storage` and document provider behavior in one place. |
