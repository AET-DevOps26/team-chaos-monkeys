# GenAI Match Verification

Status: implemented in the current codebase.

The original design replaced an earlier idea for GenAI-generated pickup
messages with a more load-bearing capability: `/verify-match`. The endpoint
lets the matching service ask the GenAI service for an independent judgment on
a candidate pair.

## Motivation

Embeddings and vector search are good at recall but can be noisy. A high
semantic score does not guarantee that two descriptions refer to the same
physical item. Verification adds an LLM reasoning step after retrieval:

1. Matching finds candidate lost/found pairs.
2. GenAI evaluates one pair at a time from descriptions and structured
   attributes.
3. Matching stores the verification signal and staff-facing rationale.

This completes the retrieval-plus-reasoning loop and gives staff more context
than `combinedScore` alone.

## Endpoint

`POST /verify-match`

Request:

- `lost.description`
- `lost.attributes`
- `found.description`
- `found.attributes`
- `language`, default `en`

Response:

- `verdict`: `match`, `no_match`, or `uncertain`
- `confidence`: float from `0.0` to `1.0`
- `rationale`: short staff-facing explanation
- `modelInfo`: provider and model

## Design Rules

- The endpoint does not receive `attributeScore`, `semanticScore`, or
  `combinedScore`; it should be an independent second opinion.
- `uncertain` is a valid result and should keep the pair in staff review.
- Guest-provided descriptions are untrusted input. The GenAI prompt treats item
  descriptions as data, not instructions.
- Verification is text/attribute based. Photos are not sent to the verifier.

## Matching-Service Persistence

The current `Match` domain model stores:

- `verifyVerdict`
- `verifyConfidence`
- `verifyRationale`
- `verifyModelProvider`
- `verifyModelName`
- `verifyCompletedAt`

These fields make verification auditable and allow the UI/API to surface why a
candidate was considered plausible.

## Testing

The GenAI test suite covers prompt/domain logic, endpoint validation, provider
error mapping, and gated real-provider checks. Matching-service tests cover
verification integration around candidate processing and persistence.
