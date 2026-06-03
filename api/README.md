# API Contracts

This directory contains source-controlled API contracts authored in the
repository. It is not the place for generated OpenAPI snapshots.

## Current Scope

- [openapi.yaml](openapi.yaml) is the contract-first synchronous API for
  `genai-service`.
- `redocly.yaml` lints and previews this contract.
- `shared/genai-client` generates GenAI DTOs from this contract during Gradle
  builds.
- `services/genai-service/app/api/schemas.py` mirrors the contract, and the
  GenAI service tests check selected schema alignment.

## Relationship To Spring Services

The Spring services are currently code-first through springdoc. Their
`/v3/api-docs` output is exposed directly by each service and aggregated through
the gateway Swagger UI.

Frontend generation is a separate flow:

- `client/openapi/*.json` stores cached springdoc snapshots from the running
  gateway.
- Orval uses those snapshots to generate `client/src/api/**`.
- `client/openapi/` snapshots should stay near the frontend because they are
  generated client inputs, not manually owned contracts.

## Maintenance

Useful commands:

```bash
npx @redocly/cli lint api/openapi.yaml
npx @redocly/cli preview-docs api/openapi.yaml
```

If more contract-first APIs are added later, either split `openapi.yaml` with
`$ref` files under this directory or add clearly named per-service specs. Do not
move generated Spring snapshots from `client/openapi/` into `api/`.
