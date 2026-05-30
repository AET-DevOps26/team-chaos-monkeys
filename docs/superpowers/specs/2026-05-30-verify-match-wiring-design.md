# Wiring `/verify-match` into matching-service

- **Date:** 2026-05-30
- **Issue:** [#151](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/151) — Wire genai-service `/verify-match` into the match confirmation flow
- **Subsystem owner:** GenAI / matching cross-cut — Luca Kollmer (`lgk03`)
- **Status:** Approved (design)
- **Supersedes:** —
- **Related specs:**
  - `2026-05-19-genai-match-verification-design.md` — the producer-side design for the `/verify-match` endpoint (already shipped in #105).

## 1. Context

`genai-service` ships `POST /verify-match` (issue #104, PR #105) — a second-opinion LLM check that compares a `LostReport` ↔ `FoundItem` pair and returns:

- `verdict` ∈ `match` | `no_match` | `uncertain`
- `confidence` ∈ [0, 1]
- `rationale` — free-text explanation
- `modelInfo` — provider + model

The endpoint is unit-tested, golden-tested, real-LLM-integration-tested, documented in `api/openapi.yaml`, instrumented end-to-end. Nothing in the platform calls it. The unique value of the endpoint over pgvector scoring is the **rationale** — pgvector produces a number, `/verify-match` produces something a staff member can read.

### What's already wired (and what isn't)

- `lost-item-service` / `found-item-service` publish `LostReportCreated.v1` and `FoundItemLogged.v1` to RabbitMQ via `RabbitTemplate` (`LostReportEventPublisher.java:27`, `FoundItemEventPublisher.java:27`).
- `matching-service` consumes those events via `IntakeEventListener` (four `@RabbitListener` queues, `IntakeEventListener.java:25,36,47,58`) and fans into `CandidateMatchingService.processIntake(...)`.
- `processIntake` runs pgvector top-K, scores each pair, filters `combinedScore ≥ 0.55`, persists a `Match` row, publishes `MatchCandidateCreated.v1`.
- `MatchController` exposes a manual `@PostMapping` for debug / sync triggering.
- **RabbitMQ is not yet in `docker-compose.yml`.** The code is wired; locally it connects to nothing. This is a separate ticket and does not block this design.

### Why this needs an explicit decision

The original ticket framed three options — A: matching-service calls verify-match after pgvector scoring; B: operations-service calls it when a staff member confirms a candidate; hybrid. Two findings during scoping reshaped the choice:

1. **Option B is structurally blocked.** `operations-service` exposes only venue + KPI endpoints (`VenueController.java`); there is no match-confirmation surface. Adding one is a much larger ticket than #151.
2. **Cost is not a real constraint.** A `gpt-4o-mini` call at ~500 in / 200 out tokens costs ~$0.0002. Even 1000 candidates/day = $0.20/day. The real constraints are **latency** (500-2000 ms per call) and **operational complexity** (calls in the hot path, retry policy, broker dependencies).

## 2. Decisions

### 2.1 Integration point: matching-service (Option A)

`/verify-match` is called from matching-service, on every candidate whose `combinedScore ≥ 0.55` (the same gate that already controls whether the candidate is persisted and published).

**Rationale.** It's the only viable surface today, and it's the one where the verdict is most useful: it influences what staff sees before they engage. Operations-side verification can be added later as a separate decision when (and if) the confirm-match flow exists.

**Alternatives rejected.** Option B (operations-side) — requires building the confirm-match endpoint first, out of #151 scope. Score-band hybrid (only verify candidates whose score lands in a configurable uncertainty band) — same code path, but requires distribution data from Grafana to pick the band; not worth the extra knob today. Tracked as a candidate follow-up.

### 2.2 Call timing: fire-and-forget async

The intake handler does **not** block on `/verify-match`. The flow:

```
broker ──[LostReportCreated/FoundItemLogged]──► matching @RabbitListener
                                                       │
                                                       └─► processIntake (listener thread)
                                                              ├─ pgvector top-K + score + filter ≥ 0.55
                                                              ├─ persist Match (verify_* NULL)
                                                              ├─ publish MatchCandidateCreated.v1 ──► broker
                                                              └─ submit verifyAsync(matchId, …)
                                                                                │
                                                                                ▼  (separate executor "genaiVerifyExecutor")
                                                                       GenaiClient.verifyMatch(...)
                                                                                │
                                                                  success ──► UPDATE match SET verify_* = ...
                                                                  failure ──► metric + WARN log; row stays NULL
```

**Rationale.** The listener thread isn't parked waiting for the LLM, so verify-match throughput is decoupled from intake throughput tuning (`spring.rabbitmq.listener.simple.concurrency`). Failures in verify-match cannot trigger intake message redelivery — the intake event is already acked once matching is done. Intake's success does not depend on LLM availability.

**Alternatives rejected.** Synchronous inline (adds 500-2000 ms per candidate to intake latency, couples broker-listener tuning to LLM tuning). Sync-with-timeout-fallthrough (same coupling, just bounded).

### 2.3 Persistence: nullable columns on the existing `Match` row

Flyway migration `services/matching-service/src/main/resources/db/migration/V<next>__add_match_verify_columns.sql`:

```sql
ALTER TABLE match
    ADD COLUMN verify_verdict          VARCHAR(16),
    ADD COLUMN verify_confidence       REAL,
    ADD COLUMN verify_rationale        TEXT,
    ADD COLUMN verify_model_provider   VARCHAR(32),
    ADD COLUMN verify_model_name       VARCHAR(64),
    ADD COLUMN verify_completed_at     TIMESTAMPTZ;

ALTER TABLE match
    ADD CONSTRAINT match_verify_verdict_chk
    CHECK (verify_verdict IS NULL OR verify_verdict IN ('match','no_match','uncertain'));
```

All columns are nullable: the row commits before verification fires, and stays null on failure. The CHECK constraint mirrors the OpenAPI enum so a misbehaving worker cannot poison the column.

**Rationale.** We only ever keep the latest verification per `Match`. There is no re-verification use case today.

**Alternatives rejected.** Separate `match_verification` table with FK — supports re-verification history, but no consumer needs it. No persistence (event-only) — RabbitMQ not yet in compose, and consumers would have to re-read anyway.

### 2.4 `shared/genai-client` extension

Add one method to `GenaiClient.java`, mirroring the existing `extractAttributes(...)`:

```java
public VerifyMatchResponse verifyMatch(VerifyMatchRequest request) {
    return restClient.post()
        .uri("/verify-match")
        .body(request)
        .retrieve()
        .body(VerifyMatchResponse.class);
}
```

`VerifyMatchRequest` / `VerifyMatchResponse` come from openapi-generator (`api/openapi.yaml` already defines them at lines 133-163; the gen config pulls all model schemas). The shared client stays **synchronous** — async is matching-service's policy choice, not a property of the reusable module.

### 2.5 Async wrapper in matching-service

A new `MatchVerificationService` owns the policy:

```java
@Service
class MatchVerificationService {
    private final GenaiClient client;
    private final MatchRepository repo;
    private final MeterRegistry meters;

    @Async("genaiVerifyExecutor")
    void verifyAsync(UUID matchId, LostReport lost, FoundItem found) {
        var timer = Timer.start(meters);
        try {
            var resp = client.verifyMatch(toRequest(lost, found));
            repo.applyVerification(matchId, resp);
            meters.counter("matching.verify.verdict_total",
                           "verdict", resp.verdict().value()).increment();
            meters.counter("matching.verify.requests_total",
                           "result", "success").increment();
        } catch (Exception e) {
            String reason = classify(e);
            meters.counter("matching.verify.requests_total",
                           "result", "error", "reason", reason).increment();
            log.warn("verify-match failed for match {}: {}", matchId, reason, e);
        } finally {
            timer.stop(meters.timer("matching.verify.duration"));
        }
    }
}
```

`@EnableAsync` lives on a new `AsyncConfig`. The executor bean is configured from properties (see 2.6). `processIntake` is amended to one extra line after the existing publish:

```java
matchRepo.save(match);
eventPublisher.publishMatchCandidateCreated(match);
verificationService.verifyAsync(match.getId(), lost, found);
```

### 2.6 Configuration (`matching-service/src/main/resources/application.properties`)

```properties
genai.verify.enabled=true
genai.verify.timeout=5s
genai.verify.executor.core-pool-size=2
genai.verify.executor.max-pool-size=8
genai.verify.executor.queue-capacity=200
```

- `enabled` allows ops to kill the path via config map without redeploying.
- `timeout` is a hard ceiling on the RestClient call (separate from the shared client's general read-timeout).
- The executor pool isolates verify-match throughput from listener concurrency.
- Queue capacity 200 with rejection policy `ThreadPoolExecutor.DiscardPolicy` — when the LLM is slow and the queue fills, new verify submissions are dropped silently and counted via `executor_full`. `CallerRunsPolicy` would re-block the listener thread, defeating the purpose.

### 2.7 Failure mode

| Source                                  | `reason` label    | Log level | Retry? | Sentry? |
|-----------------------------------------|-------------------|-----------|--------|---------|
| RestClient read timeout / `504`         | `timeout`         | WARN      | No     | No      |
| genai-service `5xx` (not timeout)       | `upstream_5xx`    | WARN      | No     | No      |
| genai-service `429`                     | `throttled`       | WARN      | No     | No      |
| genai-service `4xx` (contract mismatch) | `contract_error`  | ERROR     | No     | **Yes** |
| Executor `RejectedExecutionException`   | `executor_full`   | WARN      | No     | No      |
| Anything else                           | `unexpected`      | ERROR     | No     | **Yes** |

**No retries in v1.** Re-running matching (on a re-submitted intake event) re-issues verify-match. Bounded retry with jitter is a candidate follow-up if the failure-rate dashboard shows we need it.

When `genai.verify.enabled=false`, `verifyAsync` increments `matching.verify.requests_total{result=disabled}` and returns immediately.

### 2.8 Metrics

| Metric                              | Type                | Labels                          | Purpose                                                                       |
|-------------------------------------|---------------------|----------------------------------|-------------------------------------------------------------------------------|
| `matching.verify.requests_total`    | Counter             | `result`, `reason` (errors only) | Throughput + failure classification                                           |
| `matching.verify.verdict_total`     | Counter             | `verdict`                        | What the model is saying about candidates                                     |
| `matching.verify.duration`          | Timer               | —                                | p50 / p95 / p99 of the LLM call                                               |
| `matching.verify.confidence`        | DistributionSummary | —                                | Confidence distribution — informs whether a score-band gate is worth building |

Naming follows the existing matching-service convention (`matching.score.combined`, `matching.candidates.found_total{decision}`). Scraped via `/actuator/prometheus`. Grafana panel work belongs to the broader matching-dashboard gap, not to #151.

### 2.9 Event payload — unchanged

`MatchCandidateCreated.v1` ships without `verify_*` fields. Consumers that want the verdict read the `Match` row by `matchId` (the event already carries it). We do not add `verify_*` to the event payload in this PR.

**Rationale.** Keeps the event schema stable while the LLM call is still maturing, and avoids the awkward "event without verdict because async hadn't finished yet" state. If a downstream consumer later wants push semantics, we add a separate `MatchCandidateVerified.v1` event then.

## 3. Test scope

**Unit (Mockito):** `MatchVerificationService` — happy path plus every row of the failure-mode table. Assert the repo update fires exactly once, the right metric labels are emitted, no exception escapes, and the log level matches the table. Disabled-flag short-circuit gets its own test.

**Repository (`@DataJpaTest` + Testcontainers Postgres):** `applyVerification(matchId, response)` against a Flyway-migrated schema. Verifies the CHECK constraint by attempting to insert `verdict='bogus'` and asserting the failure.

**Slice (`@SpringBootTest` with mocked `GenaiClient`):** `processIntake(...)` returns and publishes `MatchCandidateCreated.v1` even when the mocked verify call sleeps 10 seconds — proves the listener thread isn't blocked. Then `awaitility` waits ≤ 6 s for the `verify_*` columns to populate.

**Shared client (`shared/genai-client`):** WireMock test for the new `verifyMatch(...)` wrapper — verifies the request body shape and parses a canned `VerifyMatchResponse`. Two extra error cases (`504`, `429`) exercise that the wrapper surfaces the right exception type for `MatchVerificationService.classify(...)` to bucket.

**Out of scope:** end-to-end (PowerShell suite + compose). Verify-match needs RabbitMQ in compose to fire end-to-end. The e2e check belongs in the RabbitMQ-in-compose ticket.

## 4. ADR

`services/matching-service/docs/adr/0001-verify-match-integration.md` — new ADR tree under matching-service, mirroring the genai-service `0001-image-attribute-extraction.md` precedent (per-service local ADRs, not repo-root).

Skeleton: Status / Date / Issue / Subsystem owner / Tags → Context → Decisions (numbered: integration point, sync-vs-async, persistence, failure mode, no-retry, no-event-payload) → Consequences → References. Captures the same decisions as this spec in a form a future reader can follow without the spec.

## 5. Ticket split

**Single ticket / single PR.** The Flyway migration, the shared-client method, the async service, the metrics, and the ADR are tightly coupled — none of them is independently testable. The shared-client extension is ~15 lines, not enough to warrant its own ticket.

Close **#151** with the PR. No follow-ups created upfront; the following are tracked as candidates and only escalated if observability data justifies them:

- Score-band gate (`genai.verify.score-band=…`) — depends on Grafana confidence / score distribution data being visible.
- `MatchCandidateVerified.v1` event — depends on a real consumer asking for push semantics.
- Bounded retry with jitter — depends on the failure-rate dashboard showing we need it.

## 6. Consequences

**Positive.**
- Verdict + rationale become available on every above-threshold `Match`, ready for the dashboard / notification consumers to read when they land.
- Intake latency unaffected (LLM call is off the listener thread).
- Operationally killable via config map (`genai.verify.enabled=false`) without a redeploy.
- Failure modes are classified, metered, and observable from day one.

**Negative.**
- `verify_*` columns will be NULL until either the LLM call completes successfully or the broker carries an intake event into matching-service in a real deployment. Until RabbitMQ lands in compose, local exercise of the path goes via `MatchController`'s manual endpoint.
- One extra LLM call per accepted candidate. At current scale and `gpt-4o-mini` pricing the cost is negligible (~$0.0002/call), but the cost model should be revisited if the model or the candidate volume changes materially.
- `MatchCandidateCreated.v1` consumers wanting the verdict must re-read from the DB. Acceptable for now (no such consumer exists yet); revisit if push semantics become a real requirement.

## 7. References

- Issue #151 — Wire genai-service `/verify-match` into the match confirmation flow.
- Issue #104, PR #105 — `/verify-match` endpoint (this design's dependency).
- Issue #128, PR #141 — `/extract-attributes` and `/embed` wiring; established the `GenaiClient` pattern.
- Issue #157, #167 — `shared/genai-client` module extraction and the matching-service `/embed` migration.
- `api/openapi.yaml` lines 133-163 — `VerifyMatchRequest` / `VerifyMatchResponse`.
- `services/matching-service/src/main/java/com/foundflow/matching/service/CandidateMatchingService.java` — current pgvector pipeline.
- `services/matching-service/src/main/java/com/foundflow/matching/messaging/IntakeEventListener.java` — current intake listener.
- `services/genai-service/docs/adr/0001-image-attribute-extraction.md` — ADR format precedent.
- `docs/architecture.md` §1.3 — candidate matching pipeline.
