# FoundFlow Documentation

This directory contains the current project documentation. The previous draft
documents were consolidated into the structure below; use these documents as
the current source of truth.

## Start Here

- [Application overview](product/application-overview.md) describes what
  FoundFlow does, who uses it, and which user-facing workflows exist.
- [Problem statement](product/problem-statement.md) captures the original
  product motivation and scope.
- [System architecture](architecture/system-architecture.md) describes services,
  ownership boundaries, communication paths, and runtime infrastructure.

## Architecture

- [System architecture](architecture/system-architecture.md)
- [API contract and security](architecture/api-and-security.md)
- [Messaging and domain events](architecture/messaging-and-events.md)
- [Photo storage](architecture/photo-storage.md)
- [Architecture risks](architecture/risks.md)

## Diagrams

PlantUML source files live in [diagrams](diagrams). The three course-mandated
UML artefacts:

- [Subsystem decomposition diagram](diagrams/subsystem-decomposition-diagram.puml)
- [Use case diagram](diagrams/use-case-diagram.puml)
- [Analysis object model](diagrams/analysis-object-model.puml)

Supporting design-level diagrams:

- [Class diagram](diagrams/class-diagram.puml) — design-level, with concrete types
- [Service communication diagram](diagrams/service-communication-diagram.puml)

## Operations and Course Material

- [Local Kubernetes runtime](deployment/local-kubernetes.md)
- [Project requirements](course/requirements.md)
- [Team responsibilities](course/team-responsibilities.md)
- [Research and design notes](research/)
