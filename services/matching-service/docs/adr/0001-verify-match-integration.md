# ADR 0001 — Wiring `/verify-match` into matching-service

- **Status**: Accepted
- **Date**: 2026-05-30
- **Issue**: [#151](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/151)
- **Subsystem owner**: Luca Kollmer
- **Tags**: matching-service, genai, async, observability

## Context

`genai-service` ships `POST /verify-match` (issue #104, PR #105) — a second-opinion LLM check that returns a verdict (`match`/`no_match`/`uncertain`), confidence, and free-text rationale for a `LostReport` ↔ `FoundItem` pair. The endpoint is unit-, golden-, and real-LLM-tested, documented in `api/openapi.yaml`, and instrumented end-to-end. This ADR records the decision to call it from `matching-service`.

Three integration options were considered. Operations-service ("call when staff confirms a candidate") is structurally blocked: operations-service has no match-confirmation endpoint and adding one is materially larger than #151. A score-band hybrid ("only verify candidates whose `combinedScore` lands in an uncertainty band") is the same code path as the simple integration plus one config knob, but picking the band requires distribution data we don't yet have. Matching-service-after-pgvector ("verify every above-threshold candidate") is the only viable surface today.

This ADR locks the decisions.

## Decisions

### 1. Integration point: matching-service, all above-threshold candidates

**Decision.** `/verify-match` is called from `CandidateMatchingService.processIntake(...)`, on every candidate whose `combinedScore ≥ 0.55` (the same gate that controls persistence and event publication).

**Rationale.** It is the only viable surface today and the one where the verdict is most useful — before staff sees the candidate. Cost is not a constraint (~$0.0002 per `gpt-4o-mini` call).

**Alternatives considered.** Operations-service-on-confirm — requires building the confirm-match endpoint first, out of #151 scope. Score-band hybrid — requires Grafana confidence distribution to motivate the band; tracked as a follow-up.

**Consequences.** One LLM call per persisted candidate. Operations-service can later add a second verification surface independently; the persisted column makes it trivial.

### 2. Fire-and-forget async

**Decision.** The verify call runs on a dedicated Spring `@Async` executor (`genaiVerifyExecutor`). `processIntake` does not block on it; the `@RabbitListener` thread acks the intake event as soon as matching is done.

**Rationale.** Verify-match throughput is decoupled from `spring.rabbitmq.listener.simple.concurrency`. LLM failures cannot trigger intake message redelivery — intake's success is not bound to LLM availability.

**Alternatives considered.** Synchronous inline (couples broker tuning to LLM latency; adds 500–2000 ms per candidate). Sync-with-fallthrough timeout (same coupling, just bounded).

**Consequences.** Verify-match results materialise on the `Match` row some time after `MatchCandidateCreated.v1` is published. Consumers wanting the verdict re-read from the DB.

### 3. Persistence: nullable columns on `match`

**Decision.** Six nullable columns on the existing `match` table: `verify_verdict`, `verify_confidence`, `verify_rationale`, `verify_model_provider`, `verify_model_name`, `verify_completed_at`. A CHECK constraint mirrors the OpenAPI enum for `verify_verdict`.

**Rationale.** We only keep the latest verification per `Match`; there is no re-verification use case today.

**Alternatives considered.** Separate `match_verification` table with FK — supports history, no consumer needs it. Event-only — useless until RabbitMQ lands in compose.

**Consequences.** A row exists before verification; UI must handle the NULL state gracefully (no verdict shown).

### 4. Failure mode: classify, meter, never retry, never break intake

**Decision.** Failures are classified into six labels (`timeout`, `upstream_5xx`, `throttled`, `contract_error`, `executor_full`, `unexpected`), incremented on `matching.verify.requests_total{result=error, reason=…}`, and logged at WARN (transient) or ERROR (contract / unexpected). The Match row stays NULL on failure. No retries in v1.

**Rationale.** Re-running matching (on a re-submitted intake event) re-issues verify-match. Retry policy is data-driven: add bounded retry with jitter only if the failure-rate dashboard shows it is needed.

**Alternatives considered.** Inline retry with exponential backoff — premature.

**Consequences.** A noisy genai-service can leave many Match rows with NULL verify_* fields. The metric reveals the pattern.

### 5. `MatchCandidateCreated.v1` event payload unchanged

**Decision.** No `verify_*` fields on the event. Consumers read the Match row by `matchId`.

**Rationale.** Stable schema while the LLM call matures; avoids the awkward "event without verdict because async hadn't finished" state.

**Alternatives considered.** Emit a second `MatchCandidateVerified.v1` event on completion — no consumer wants push semantics yet.

**Consequences.** If a real consumer asks for push semantics, we add the second event; existing consumers don't have to change.

### 6. Kill-switch via configuration

**Decision.** `genai.verify.enabled=true|false` lets ops disable the path via config map without redeploying. When disabled, `verifyAsync` increments `matching.verify.requests_total{result=disabled}` and returns.

**Rationale.** Cheap to add, valuable for incident response.

**Consequences.** None negative.

## Consequences (summary)

- The verdict + rationale become available on every above-threshold `Match`, ready for dashboard / notification consumers.
- Intake latency unaffected.
- Operationally killable without a redeploy.
- Failure modes observable from day one.
- Local exercise of the path goes through normal intake events; `MatchController` also exposes manual endpoints for targeted checks.

## References

- Issue #151; depends on #104 (endpoint design), #105 (endpoint PR).
- `api/openapi.yaml` lines 133–163 — `VerifyMatchRequest` / `VerifyMatchResponse`.
- `services/genai-service/docs/adr/0001-image-attribute-extraction.md` — ADR format precedent.
