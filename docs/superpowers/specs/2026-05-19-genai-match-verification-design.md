# GenAI match verification & explanation — Design

|               |                                                                                  |
| ------------- | -------------------------------------------------------------------------------- |
| **Feature**   | `POST /verify-match` — LLM verification and explanation of candidate matches      |
| **Issue**     | New — to be created (sub-issue of GenAI epic #7); supersedes #53                  |
| **Subsystem** | GenAI service (`services/genai-service/`)                                        |
| **Author**    | Luca Kollmer (`lgk03`)                                                           |
| **Date**      | 2026-05-19                                                                       |
| **Status**    | Approved — ready for implementation planning                                     |
| **Branch**    | `feat/genai-verify-match` → `development`                                        |

## 1. Context and motivation

The genai-service has two live capabilities — attribute extraction (#49) and embeddings (#50) — plus a third, guest pickup-notification text generation (`/generate-message`, #53), which was built but not merged (PR #100).

A team review concluded `/generate-message` is not worth keeping: the pickup notification is fully templatable, the feature is not load-bearing (the system does not degrade without it — `notification-service` falls back to a static template), and it carries a hidden dependency on per-guest language capture that is not built. The GenAI requirement is already met by extraction + embeddings.

A genuine gap does remain, though. The system claims "RAG" but implements only the *retrieval* half — embeddings + vector similarity. Retrieval-augmented *generation* — an LLM reasoning over the retrieved candidates — is missing. And the matching pipeline surfaces only an opaque `combinedScore` to staff.

**This feature replaces `/generate-message` with `/verify-match`:** an LLM pass that verifies a candidate match (lost report vs found item) and explains it. It completes the RAG pattern, makes the LLM genuinely load-bearing in matching (it improves match precision), and gives staff an explanation instead of an opaque score.

## 2. Decision

- **Drop `/generate-message`** entirely — close PR #100 unmerged, strip the endpoint from the API contract, close the related issues.
- **Add `/verify-match`** to the genai-service.
- **Scope:** this design and its plan cover the GenAI endpoint, the contract `matching-service` will call, the `/generate-message` teardown, and documentation. The `matching-service` recall stage and call wiring are *specified as an interface* but implemented separately (backend / Johannes).

## 3. The endpoint

`POST /verify-match` in the genai-service, `operationId: verifyMatch`. It follows the established two-layer pattern exactly — a thin router (`app/api/verify.py`) over a domain module (`app/verification.py`), `chat(json_mode=True)`, output validated against an internal Pydantic model, the shared error envelope from `app/errors.py`.

### Request — `VerifyMatchRequest`

- `lost`: `ItemSide` — the lost-report side.
- `found`: `ItemSide` — the candidate found-item side.
- `language`: optional ISO-639-1 code for the rationale's language; defaults to `en`.

`ItemSide` (shared sub-schema):
- `description`: string, required — the free-text description.
- `attributes`: `ItemAttributes`, optional — the structured attributes if extracted. Reuses the existing `ItemAttributes` schema.

### Response — `VerifyMatchResponse`

- `verdict`: enum — `match` | `no_match` | `uncertain`.
- `confidence`: float in [0, 1] — the model's confidence in the verdict.
- `rationale`: string — a short plain-text explanation citing the specific overlaps and conflicts. Staff-facing.
- `modelInfo`: `ModelInfo`.

Errors: 400 / 422 / 429 / 500 / 502 / 504 — the shared flat `ErrorResponse` envelope, identical to the other endpoints.

### Design principles

- **Independent judgment.** The endpoint is *not* given the matching-service's `attributeScore` / `semanticScore`. It judges purely from item content, so it is a genuine second opinion that can catch vector-search false positives — not a rubber stamp of the score that produced the candidate. The matching-service combines the LLM verdict with its own scores.
- **`uncertain` is a first-class verdict.** The model is not forced into a binary; genuine ambiguity returns `uncertain`, telling the matching-service to keep the pair as a staff-review candidate.
- **Untrusted input.** `lost.description` is guest free text. `build_messages` fences both descriptions as delimited data and the system prompt forbids acting on instructions inside them — the same prompt-injection defense as `/extract-attributes`.

## 4. Domain logic

`app/verification.py` mirrors `app/extraction.py`:

- `SYSTEM_PROMPT` — instructs the model to judge whether the two descriptions refer to the same physical item, to return `uncertain` on genuine ambiguity, to ground the rationale in specific overlaps and conflicts, to write the rationale in the requested language, plain text only, and to treat the fenced item details as data, never instructions.
- `build_messages(payload)` — system + user message; both item descriptions fenced in a triple-quote delimiter; structured attributes included when present.
- `verify_match(payload, llm)` — `chat(json_mode=True)`, then parse and validate; returns the verdict, confidence, and rationale.
- `parse_verification(raw)` — JSON parse + Pydantic validation against an internal `VerificationOutput` model (`verdict`, `confidence`, `rationale`); raises `ModelOutputError` (HTTP 422) on malformed JSON, wrong shape, an unknown `verdict`, or a `confidence` outside [0, 1].

`modelInfo` comes from `resolve_model_info(settings)` (chat kind), imported from `app/extraction.py` as the other endpoints do.

## 5. matching-service interface (specified, not built)

For `matching-service` to consume `/verify-match` (Johannes's work — defined here as the contract it builds against):

- `matching-service` does vector + attribute **recall** to produce candidate pairs.
- For its top-K candidates per lost report, it calls `POST /verify-match` once per pair.
- It persists `verdict`, `confidence`, and `rationale` on the `Match` record (new columns) and surfaces the rationale to staff alongside `combinedScore`.
- Gating (which and how many pairs to verify) and combining the verdict with the existing scores are matching-service decisions — kept on the caller's side for cost control.

This design does **not** modify `matching-service`. The new `Match` columns and the call wiring are Johannes's; this spec is the interface.

## 6. Removing `/generate-message`

- Close PR #100 unmerged; delete the `feat/genai-generate-message` branch.
- `api/openapi.yaml` (on `development`): remove the `/generate-message` path and the `GenerateMessageRequest`, `PickupContext`, and `GenerateMessageResponse` schemas (added in #48).
- Close issue #53 as not-planned, referencing this design.
- Close issue #101 (notification-service static-template fallback) — moot without `/generate-message`.
- Correct the comment on #94 — `/verify-match`, not `/generate-message`, is now the third `resolve_model_info` consumer.
- The `/generate-message` *code* never merged to `development` (it lived only on the PR branch), so no code revert is needed there.

## 7. Documentation updates

- `docs/problem-statement.md` §4 — capability (c) "Outbound message generation" → "Match verification & explanation"; remove the LLM-notification mention from Scenarios A/B; Scenario C's "short rationale per hit" now describes a real endpoint.
- `docs/architecture.md` — the genai-service capability description and the service-relationship table (`notification-service` no longer calls genai-service; `matching-service` now calls it for verification).
- `services/genai-service/README.md` — the endpoints table and the roadmap line.
- `api/openapi.yaml` — add the `/verify-match` path and the `VerifyMatchRequest` / `ItemSide` / `VerifyMatchResponse` schemas (alongside the `/generate-message` removal in §6).

## 8. Testing

Mirrors the established genai-service pattern:

- `tests/test_verification.py` — domain unit tests with `FakeProvider`: prompt building (both descriptions fenced), `verify_match` happy path, `parse_verification` rejecting malformed JSON / wrong shape / unknown verdict / out-of-range confidence, provider-error propagation.
- `tests/test_verify_match.py` — endpoint tests with `TestClient`: happy path for each verdict, camelCase serialisation, 400 request validation, 422 model-output validation, provider-error mapping, flat error envelope, uncaught-500.
- `tests/integration/test_real_verification.py` — a gated (`GENAI_RUN_VERIFICATION`) real-LLM check that the prompt yields schema-valid verdicts on a real model across a few crafted match / no-match / uncertain pairs.

## 9. Out of scope

- `matching-service` internals — recall (vector + attribute candidate generation), event consumption, the call wiring, the new `Match` columns, scoring, staff-dashboard surfacing. Backend / Johannes.
- Image / multimodal verification — verification is text-only (descriptions + attributes); the photo is not sent to the model. Image work remains parked as #90.
- GenAI observability metrics — #54.
- Relocating `resolve_model_info` and the shared helpers — #94.
