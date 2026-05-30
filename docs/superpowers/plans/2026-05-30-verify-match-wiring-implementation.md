# Wiring `/verify-match` into matching-service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `genai-service POST /verify-match` into `matching-service` so every above-threshold candidate gets an LLM second-opinion verdict + rationale, persisted on the `Match` row, called fire-and-forget async off the RabbitMQ listener thread.

**Architecture:** New `MatchVerificationService` in matching-service consumes the existing `shared/genai-client` (one method added: `verifyMatch(...)`). The service is invoked via Spring `@Async` on a dedicated executor immediately after `MatchCandidateCreated.v1` is published in `CandidateMatchingService.processIntake`. The verdict, confidence, rationale, model provenance, and completion timestamp are persisted as nullable columns on the existing `match` table via a Flyway V5 migration. All failure modes are classified and metered; no retries in v1; configurable kill-switch via `genai.verify.enabled`.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Spring AMQP (already wired), JPA, Flyway, Micrometer, JUnit 5 + Mockito, Testcontainers Postgres (already on classpath), WireMock + Awaitility (added in T2).

**Spec:** `docs/superpowers/specs/2026-05-30-verify-match-wiring-design.md`
**Issue:** [#151](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/151)
**Branch base:** `development`

---

## Prerequisites

- Issue #167 (`matching-service: migrate /embed client to shared/genai-client`) is expected to merge first; it adds `includeBuild('../../shared/genai-client')` to `services/matching-service/settings.gradle` and the `com.foundflow:genai-client` dependency. **Task 1 verifies and, if absent, adds these.** The plan works either order.
- No Sentry SDK is wired in matching-service today. The spec's "Sentry: Yes" entries for `contract_error` and `unexpected` are realised here as `log.error(...)` with full context; promotion to a real Sentry capture is a separate follow-up when issue #91 (Sentry vs OTel decision) lands.

---

## Task 1: Branch setup, prerequisite check, ADR

**Files:**
- Create: `services/matching-service/docs/adr/0001-verify-match-integration.md`
- Possibly modify: `services/matching-service/settings.gradle`
- Possibly modify: `services/matching-service/build.gradle`

- [ ] **Step 1: Cut a fresh branch from up-to-date `development`**

```bash
git fetch origin
git checkout development
git pull --ff-only
git checkout -b feat/151-verify-match-wiring
```

Expected: `Switched to a new branch 'feat/151-verify-match-wiring'`.

- [ ] **Step 2: Verify or add `shared/genai-client` to matching-service**

Check whether #167 has landed:

```bash
grep -F "includeBuild('../../shared/genai-client')" services/matching-service/settings.gradle
```

If output is non-empty, skip to Step 3. Otherwise add the line:

```groovy
// services/matching-service/settings.gradle
includeBuild('../../shared/genai-client')
```

Place it directly under the existing `includeBuild('../../shared/domain-events')` line. Then add the dependency to `services/matching-service/build.gradle` in the `dependencies { }` block (next to the existing `com.foundflow:domain-events` line):

```groovy
implementation 'com.foundflow:genai-client:0.0.1-SNAPSHOT'
```

- [ ] **Step 3: Verify Gradle resolves the dependency**

```bash
./gradlew :matching-service:dependencies --configuration runtimeClasspath | grep genai-client
```

Expected: a line like `+--- com.foundflow:genai-client:0.0.1-SNAPSHOT`.

- [ ] **Step 4: Write the ADR**

Create `services/matching-service/docs/adr/0001-verify-match-integration.md` with the exact content below. The structure mirrors `services/genai-service/docs/adr/0001-image-attribute-extraction.md`.

```markdown
# ADR 0001 — Wiring `/verify-match` into matching-service

- **Status**: Accepted
- **Date**: 2026-05-30
- **Issue**: [#151](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/151)
- **Subsystem owner**: Luca Kollmer
- **Tags**: matching-service, genai, async, observability

## Context

`genai-service` ships `POST /verify-match` (issue #104, PR #105) — a second-opinion LLM check that returns a verdict (`match`/`no_match`/`uncertain`), confidence, and free-text rationale for a `LostReport` ↔ `FoundItem` pair. The endpoint is unit-, golden-, and real-LLM-tested, documented in `api/openapi.yaml`, and instrumented end-to-end. Nothing in the platform calls it.

Three integration options were considered. Operations-service ("call when staff confirms a candidate") is structurally blocked: operations-service has no match-confirmation endpoint and adding one is materially larger than #151. A score-band hybrid ("only verify candidates whose `combinedScore` lands in an uncertainty band") is the same code path as the simple integration plus one config knob, but picking the band requires distribution data we don't yet have. Matching-service-after-pgvector ("verify every above-threshold candidate") is the only viable surface today.

The full design is captured in `docs/superpowers/specs/2026-05-30-verify-match-wiring-design.md`. This ADR locks the decisions.

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
- Until RabbitMQ lands in compose, local exercise of the path goes via `MatchController`'s manual endpoint.

## References

- Spec: `docs/superpowers/specs/2026-05-30-verify-match-wiring-design.md`
- Issue #151; depends on #104 (endpoint design), #105 (endpoint PR).
- `api/openapi.yaml` lines 133–163 — `VerifyMatchRequest` / `VerifyMatchResponse`.
- `services/genai-service/docs/adr/0001-image-attribute-extraction.md` — ADR format precedent.
```

- [ ] **Step 5: Commit**

```bash
git add services/matching-service/docs/adr/0001-verify-match-integration.md \
        services/matching-service/settings.gradle \
        services/matching-service/build.gradle
git commit -m "docs(matching): ADR 0001 verify-match integration; wire shared/genai-client

Refs #151."
```

---

## Task 2: Add test dependencies (WireMock + Awaitility)

**Files:**
- Modify: `services/matching-service/build.gradle`
- Modify: `shared/genai-client/build.gradle`

WireMock is needed in both modules; Awaitility only in matching-service for the async slice test.

- [ ] **Step 1: Add deps to matching-service**

In `services/matching-service/build.gradle`, inside the `dependencies { }` block, add to the test deps:

```groovy
testImplementation 'org.wiremock:wiremock-standalone:3.10.0'
testImplementation 'org.awaitility:awaitility:4.2.2'
```

- [ ] **Step 2: Add WireMock to shared/genai-client tests**

In `shared/genai-client/build.gradle`, inside the `dependencies { }` block, add:

```groovy
testImplementation 'org.wiremock:wiremock-standalone:3.10.0'
```

- [ ] **Step 3: Verify Gradle resolves**

```bash
./gradlew :matching-service:dependencies --configuration testRuntimeClasspath | grep -E "wiremock|awaitility"
./gradlew -p shared/genai-client dependencies --configuration testRuntimeClasspath | grep wiremock
```

Expected: lines for both `wiremock-standalone` and `awaitility` in matching-service; `wiremock-standalone` in shared/genai-client.

- [ ] **Step 4: Commit**

```bash
git add services/matching-service/build.gradle shared/genai-client/build.gradle
git commit -m "build: add WireMock + Awaitility for verify-match wiring tests

Refs #151."
```

---

## Task 3: Flyway V5 migration

**Files:**
- Create: `services/matching-service/src/main/resources/db/migration/V5__add_match_verify_columns.sql`
- Create: `services/matching-service/src/test/java/com/foundflow/matching/repository/MatchVerifyMigrationIT.java`

- [ ] **Step 1: Write the failing migration IT**

Create `services/matching-service/src/test/java/com/foundflow/matching/repository/MatchVerifyMigrationIT.java`:

```java
package com.foundflow.matching.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchVerifyMigrationIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void verifyColumnsExistOnMatchTable() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'match' AND column_name LIKE 'verify_%'",
                Integer.class);
        assertThat(count).isEqualTo(6);
    }

    @Test
    void verifyVerdictCheckConstraintRejectsInvalidValues() {
        java.util.UUID matchId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO match (id, found_item_id, lost_report_id, venue_id, status,
                                   attribute_score, semantic_score, combined_score, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 1.0, 0.9, 0.9, NOW())
                """,
                matchId, java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID());

        assertThatThrownBy(() ->
                jdbc.update("UPDATE match SET verify_verdict = ? WHERE id = ?", "bogus", matchId))
                .hasMessageContaining("match_verify_verdict_chk");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :matching-service:test --tests '*MatchVerifyMigrationIT*'
```

Expected: FAIL — `count` will be 0 (the migration doesn't exist yet) and the CHECK constraint also won't exist.

- [ ] **Step 3: Write the migration**

Create `services/matching-service/src/main/resources/db/migration/V5__add_match_verify_columns.sql`:

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

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :matching-service:test --tests '*MatchVerifyMigrationIT*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/matching-service/src/main/resources/db/migration/V5__add_match_verify_columns.sql \
        services/matching-service/src/test/java/com/foundflow/matching/repository/MatchVerifyMigrationIT.java
git commit -m "feat(matching): V5 migration — verify_* columns on match table

Refs #151."
```

---

## Task 4: `Match` entity columns + `MatchRepository.applyVerification`

**Files:**
- Modify: `services/matching-service/src/main/java/com/foundflow/matching/domain/Match.java`
- Modify: `services/matching-service/src/main/java/com/foundflow/matching/repository/MatchRepository.java`
- Create: `services/matching-service/src/test/java/com/foundflow/matching/repository/MatchRepositoryApplyVerificationIT.java`

- [ ] **Step 1: Write the failing repository IT**

Create `services/matching-service/src/test/java/com/foundflow/matching/repository/MatchRepositoryApplyVerificationIT.java`:

```java
package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.domain.MatchVerification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchRepositoryApplyVerificationIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MatchRepository repo;

    @Test
    void applyVerification_setsAllSixColumns() {
        Match m = repo.save(new Match(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchStatus.PENDING, 1.0f, 0.9f, 0.9f, LocalDateTime.now()));

        OffsetDateTime completedAt = OffsetDateTime.now();
        repo.applyVerification(m.getId(), new MatchVerification(
                "match", 0.87f, "Both describe a blue Patagonia jacket.",
                "openai", "gpt-4o-mini", completedAt));

        Match reloaded = repo.findById(m.getId()).orElseThrow();
        assertThat(reloaded.getVerifyVerdict()).isEqualTo("match");
        assertThat(reloaded.getVerifyConfidence()).isEqualTo(0.87f);
        assertThat(reloaded.getVerifyRationale()).isEqualTo("Both describe a blue Patagonia jacket.");
        assertThat(reloaded.getVerifyModelProvider()).isEqualTo("openai");
        assertThat(reloaded.getVerifyModelName()).isEqualTo("gpt-4o-mini");
        assertThat(reloaded.getVerifyCompletedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run to verify it fails to compile**

```bash
./gradlew :matching-service:compileTestJava
```

Expected: FAIL — `MatchVerification` doesn't exist, `applyVerification` method doesn't exist, getters don't exist.

- [ ] **Step 3: Create the `MatchVerification` value object**

Create `services/matching-service/src/main/java/com/foundflow/matching/domain/MatchVerification.java`:

```java
package com.foundflow.matching.domain;

import java.time.OffsetDateTime;

public record MatchVerification(
        String verdict,
        Float confidence,
        String rationale,
        String modelProvider,
        String modelName,
        OffsetDateTime completedAt
) {
}
```

- [ ] **Step 4: Add the columns + getters/setters to `Match`**

In `services/matching-service/src/main/java/com/foundflow/matching/domain/Match.java`, add the six fields (plain JPA `@Column` annotations, matching the style of existing fields):

```java
    @Column(name = "verify_verdict")
    private String verifyVerdict;

    @Column(name = "verify_confidence")
    private Float verifyConfidence;

    @Column(name = "verify_rationale", columnDefinition = "TEXT")
    private String verifyRationale;

    @Column(name = "verify_model_provider")
    private String verifyModelProvider;

    @Column(name = "verify_model_name")
    private String verifyModelName;

    @Column(name = "verify_completed_at")
    private java.time.OffsetDateTime verifyCompletedAt;
```

Add matching getters and setters following the file's existing style (plain `public T getX() { return x; } public void setX(T x) { this.x = x; }`).

- [ ] **Step 5: Add `applyVerification` to `MatchRepository`**

In `services/matching-service/src/main/java/com/foundflow/matching/repository/MatchRepository.java`, add a `@Modifying` `@Query` method:

```java
import com.foundflow.matching.domain.MatchVerification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

// inside the interface:

@Modifying
@Transactional
@Query("""
    UPDATE Match m
       SET m.verifyVerdict = :#{#v.verdict()},
           m.verifyConfidence = :#{#v.confidence()},
           m.verifyRationale = :#{#v.rationale()},
           m.verifyModelProvider = :#{#v.modelProvider()},
           m.verifyModelName = :#{#v.modelName()},
           m.verifyCompletedAt = :#{#v.completedAt()}
     WHERE m.id = :matchId
    """)
int applyVerification(@Param("matchId") UUID matchId, @Param("v") MatchVerification v);
```

Add the imports for `@Param` (`org.springframework.data.repository.query.Param`) and `MatchVerification` if missing.

- [ ] **Step 6: Run the IT to verify it passes**

```bash
./gradlew :matching-service:test --tests '*MatchRepositoryApplyVerificationIT*'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/matching-service/src/main/java/com/foundflow/matching/domain/Match.java \
        services/matching-service/src/main/java/com/foundflow/matching/domain/MatchVerification.java \
        services/matching-service/src/main/java/com/foundflow/matching/repository/MatchRepository.java \
        services/matching-service/src/test/java/com/foundflow/matching/repository/MatchRepositoryApplyVerificationIT.java
git commit -m "feat(matching): Match.verify_* fields + applyVerification repo method

Refs #151."
```

---

## Task 5: Surface `text_source` from `findTopKSimilar`

The pgvector candidate-search projection currently returns `(itemId, category, cosineDistance)` only. To call `/verify-match` we need each side's description. The originating side already has it (it's a `processIntake` parameter). The candidate side needs to come out of the pgvector query.

**Files:**
- Modify: `services/matching-service/src/main/java/com/foundflow/matching/repository/SimilarItemEmbedding.java`
- Modify: `services/matching-service/src/main/java/com/foundflow/matching/repository/ItemEmbeddingRepository.java`
- Modify: `services/matching-service/src/test/java/com/foundflow/matching/repository/ItemEmbeddingRepositoryIT.java`

- [ ] **Step 1: Update the IT to assert text_source on the projection**

Open `services/matching-service/src/test/java/com/foundflow/matching/repository/ItemEmbeddingRepositoryIT.java`. Find the existing `findTopKSimilar` test (it asserts the returned candidate's `itemId`, `category`, `cosineDistance`). Add an assertion for `textSource()`:

```java
assertThat(top.textSource()).isEqualTo("blue patagonia jacket, lobby");
```

…where `"blue patagonia jacket, lobby"` is whatever value the test arranged via `upsert(...)` for the candidate row. If the existing test doesn't set a specific `textSource`, set one and assert it.

- [ ] **Step 2: Run to verify it fails to compile**

```bash
./gradlew :matching-service:compileTestJava
```

Expected: FAIL — `SimilarItemEmbedding` has no `textSource()` accessor.

- [ ] **Step 3: Add `textSource` to the projection**

Replace `services/matching-service/src/main/java/com/foundflow/matching/repository/SimilarItemEmbedding.java`:

```java
package com.foundflow.matching.repository;

import java.util.UUID;

public record SimilarItemEmbedding(
        UUID itemId,
        String category,
        String textSource,
        float cosineDistance
) {
}
```

- [ ] **Step 4: Update the SQL + RowMapper**

In `services/matching-service/src/main/java/com/foundflow/matching/repository/ItemEmbeddingRepository.java`, update `findTopKSimilar`:

```java
return jdbcTemplate.query(
        """
        SELECT item_id, category, text_source, embedding <=> ? AS distance
        FROM item_embeddings
        WHERE item_type = ? AND venue_id = ?
        ORDER BY embedding <=> ?
        LIMIT ?
        """,
        (rs, n) -> new SimilarItemEmbedding(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getFloat(4)
        ),
        query,
        oppositeType.name(),
        venueId,
        query,
        k
);
```

- [ ] **Step 5: Fix any compile errors elsewhere**

`CandidateMatchingServiceTest` and `CandidateMatchingService` likely construct `SimilarItemEmbedding` directly. Add the `textSource` parameter to every constructor call:

```bash
grep -rn "new SimilarItemEmbedding(" services/matching-service/src/
```

Update each call site to include a non-null `String textSource` (use `"test text source"` in tests, or the real `textSource` from the embedding row).

- [ ] **Step 6: Run to verify all tests pass**

```bash
./gradlew :matching-service:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/matching-service/src/main/java/com/foundflow/matching/repository/SimilarItemEmbedding.java \
        services/matching-service/src/main/java/com/foundflow/matching/repository/ItemEmbeddingRepository.java \
        services/matching-service/src/test/java/com/foundflow/matching/repository/ItemEmbeddingRepositoryIT.java \
        services/matching-service/src/test/java/com/foundflow/matching/service/CandidateMatchingServiceTest.java
git commit -m "feat(matching): surface text_source on pgvector candidate projection

Needed by verify-match wiring (#151)."
```

---

## Task 6: Extend `shared/genai-client` with `verifyMatch(...)`

**Files:**
- Modify: `shared/genai-client/src/main/java/com/foundflow/genai/client/GenaiClient.java`
- Create: `shared/genai-client/src/test/java/com/foundflow/genai/client/GenaiClientVerifyMatchTest.java`

- [ ] **Step 1: Write the failing WireMock test**

Create `shared/genai-client/src/test/java/com/foundflow/genai/client/GenaiClientVerifyMatchTest.java`:

```java
package com.foundflow.genai.client;

import com.foundflow.genai.client.model.LostItemDescription;
import com.foundflow.genai.client.model.FoundItemDescription;
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenaiClientVerifyMatchTest {

    WireMockServer wm;
    GenaiClient client;

    @BeforeEach
    void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        client = new GenaiClient(RestClient.builder().baseUrl(wm.baseUrl()).build());
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    @Test
    void postsToVerifyMatchAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .withRequestBody(matchingJsonPath("$.lost.description", equalTo("blue jacket lobby")))
                .withRequestBody(matchingJsonPath("$.found.description", equalTo("navy jacket reception")))
                .willReturn(okJson("""
                        {
                          "verdict": "match",
                          "confidence": 0.87,
                          "rationale": "Both describe a navy/blue jacket near reception/lobby.",
                          "modelInfo": { "provider": "openai", "model": "gpt-4o-mini" }
                        }
                        """)));

        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new LostItemDescription().description("blue jacket lobby"))
                .found(new FoundItemDescription().description("navy jacket reception"));

        VerifyMatchResponse resp = client.verifyMatch(req);

        assertThat(resp.getVerdict().getValue()).isEqualTo("match");
        assertThat(resp.getConfidence()).isEqualTo(0.87f);
        assertThat(resp.getRationale()).contains("navy/blue jacket");
        assertThat(resp.getModelInfo().getProvider()).isEqualTo("openai");
        assertThat(resp.getModelInfo().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void surfaces504AsServerErrorException() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .willReturn(aResponse().withStatus(504)));

        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new LostItemDescription().description("x"))
                .found(new FoundItemDescription().description("y"));

        assertThatThrownBy(() -> client.verifyMatch(req))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void surfaces429AsClientErrorException() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .willReturn(aResponse().withStatus(429)));

        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new LostItemDescription().description("x"))
                .found(new FoundItemDescription().description("y"));

        assertThatThrownBy(() -> client.verifyMatch(req))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);
    }
}
```

**Note on generated model accessors.** The exact accessors (`.getVerdict().getValue()` vs `.verdict()`, builder-style `.lost(...)` vs constructor) depend on how `shared/genai-client`'s `openApiGenerate` is configured. If the generated `VerifyMatchRequest` doesn't have builder-style setters, replace the construction with whatever the generator emits (constructor + setter, all-args constructor, etc.). The test's intent is: send a JSON body with `lost.description` and `found.description`, parse a verdict / confidence / rationale / modelInfo from the response.

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew -p shared/genai-client test --tests '*GenaiClientVerifyMatchTest*'
```

Expected: FAIL — `GenaiClient` has no `verifyMatch` method.

- [ ] **Step 3: Add the method to `GenaiClient`**

Edit `shared/genai-client/src/main/java/com/foundflow/genai/client/GenaiClient.java`. Add the import and method:

```java
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;

// inside the class, mirroring extractAttributes(...):
public VerifyMatchResponse verifyMatch(VerifyMatchRequest request) {
    return restClient.post()
            .uri("/verify-match")
            .body(request)
            .retrieve()
            .body(VerifyMatchResponse.class);
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew -p shared/genai-client test --tests '*GenaiClientVerifyMatchTest*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/genai-client/src/main/java/com/foundflow/genai/client/GenaiClient.java \
        shared/genai-client/src/test/java/com/foundflow/genai/client/GenaiClientVerifyMatchTest.java
git commit -m "feat(genai-client): add verifyMatch(...) for /verify-match endpoint

Refs #151."
```

---

## Task 7: Configuration properties + executor

**Files:**
- Create: `services/matching-service/src/main/java/com/foundflow/matching/config/GenaiVerifyProperties.java`
- Create: `services/matching-service/src/main/java/com/foundflow/matching/config/AsyncConfig.java`
- Modify: `services/matching-service/src/main/resources/application.properties`

- [ ] **Step 1: Create `GenaiVerifyProperties`**

`services/matching-service/src/main/java/com/foundflow/matching/config/GenaiVerifyProperties.java`:

```java
package com.foundflow.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "genai.verify")
public class GenaiVerifyProperties {

    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(5);
    private Executor executor = new Executor();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }

    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 8;
        private int queueCapacity = 200;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int v) { this.corePoolSize = v; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int v) { this.maxPoolSize = v; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int v) { this.queueCapacity = v; }
    }
}
```

- [ ] **Step 2: Create `AsyncConfig`**

`services/matching-service/src/main/java/com/foundflow/matching/config/AsyncConfig.java`:

```java
package com.foundflow.matching.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(GenaiVerifyProperties.class)
public class AsyncConfig {

    @Bean(name = "genaiVerifyExecutor")
    public Executor genaiVerifyExecutor(GenaiVerifyProperties props) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(props.getExecutor().getCorePoolSize());
        ex.setMaxPoolSize(props.getExecutor().getMaxPoolSize());
        ex.setQueueCapacity(props.getExecutor().getQueueCapacity());
        ex.setThreadNamePrefix("genai-verify-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.initialize();
        return ex;
    }
}
```

- [ ] **Step 3: Add properties to `application.properties`**

Append to `services/matching-service/src/main/resources/application.properties`:

```properties
# verify-match (issue #151)
genai.verify.enabled=${GENAI_VERIFY_ENABLED:true}
genai.verify.timeout=${GENAI_VERIFY_TIMEOUT:5s}
genai.verify.executor.core-pool-size=${GENAI_VERIFY_CORE_POOL:2}
genai.verify.executor.max-pool-size=${GENAI_VERIFY_MAX_POOL:8}
genai.verify.executor.queue-capacity=${GENAI_VERIFY_QUEUE:200}
```

- [ ] **Step 4: Verify the context loads**

```bash
./gradlew :matching-service:test --tests '*MatchingServiceApplicationTests*'
```

Expected: PASS (the empty Spring context smoke test still loads with the new beans).

- [ ] **Step 5: Commit**

```bash
git add services/matching-service/src/main/java/com/foundflow/matching/config/GenaiVerifyProperties.java \
        services/matching-service/src/main/java/com/foundflow/matching/config/AsyncConfig.java \
        services/matching-service/src/main/resources/application.properties
git commit -m "feat(matching): async executor + config for verify-match

Refs #151."
```

---

## Task 8: `MatchVerificationService` + unit tests

**Files:**
- Create: `services/matching-service/src/main/java/com/foundflow/matching/service/MatchVerificationService.java`
- Create: `services/matching-service/src/test/java/com/foundflow/matching/service/MatchVerificationServiceTest.java`

- [ ] **Step 1: Write the failing unit tests**

Create `services/matching-service/src/test/java/com/foundflow/matching/service/MatchVerificationServiceTest.java`:

```java
package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.*;
import com.foundflow.matching.config.GenaiVerifyProperties;
import com.foundflow.matching.domain.MatchVerification;
import com.foundflow.matching.repository.MatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchVerificationServiceTest {

    GenaiClient client;
    MatchRepository repo;
    MeterRegistry meters;
    GenaiVerifyProperties props;
    MatchVerificationService svc;
    UUID matchId;

    @BeforeEach
    void setUp() {
        client = mock(GenaiClient.class);
        repo = mock(MatchRepository.class);
        meters = new SimpleMeterRegistry();
        props = new GenaiVerifyProperties();
        svc = new MatchVerificationService(client, repo, meters, props);
        matchId = UUID.randomUUID();
    }

    private VerifyMatchResponse okResponse(String verdict) {
        VerifyMatchResponse r = new VerifyMatchResponse();
        r.setVerdict(VerifyMatchResponse.VerdictEnum.fromValue(verdict));
        r.setConfidence(0.9f);
        r.setRationale("ok");
        ModelInfo mi = new ModelInfo();
        mi.setProvider("openai");
        mi.setModel("gpt-4o-mini");
        r.setModelInfo(mi);
        return r;
    }

    @Test
    void happyPath_appliesVerificationAndIncrementsCounters() {
        when(client.verifyMatch(any())).thenReturn(okResponse("match"));

        svc.verifyAsync(matchId, "lost text", "found text");

        ArgumentCaptor<MatchVerification> v = ArgumentCaptor.forClass(MatchVerification.class);
        verify(repo).applyVerification(eq(matchId), v.capture());
        assertThat(v.getValue().verdict()).isEqualTo("match");
        assertThat(v.getValue().modelProvider()).isEqualTo("openai");

        assertThat(meters.counter("matching.verify.requests_total", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(meters.counter("matching.verify.verdict_total", "verdict", "match").count())
                .isEqualTo(1.0);
    }

    @Test
    void disabledFlag_shortCircuitsWithoutCallingClient() {
        props.setEnabled(false);

        svc.verifyAsync(matchId, "lost", "found");

        verifyNoInteractions(client, repo);
        assertThat(meters.counter("matching.verify.requests_total", "result", "disabled").count())
                .isEqualTo(1.0);
    }

    @Test
    void timeout504_classifiedAsTimeoutAndRowStaysNull() {
        when(client.verifyMatch(any())).thenThrow(HttpServerErrorException.GatewayTimeout.create(
                org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "504", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        verifyNoInteractions(repo);
        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "timeout").count()).isEqualTo(1.0);
    }

    @Test
    void upstream5xx_classifiedAsUpstream5xx() {
        when(client.verifyMatch(any())).thenThrow(HttpServerErrorException.create(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "502", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "upstream_5xx").count()).isEqualTo(1.0);
    }

    @Test
    void throttled429_classifiedAsThrottled() {
        when(client.verifyMatch(any())).thenThrow(HttpClientErrorException.TooManyRequests.create(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "429", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "throttled").count()).isEqualTo(1.0);
    }

    @Test
    void contract4xx_classifiedAsContractErrorAtErrorLevel() {
        when(client.verifyMatch(any())).thenThrow(HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST, "400", null, null, null));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "contract_error").count()).isEqualTo(1.0);
    }

    @Test
    void executorRejection_classifiedAsExecutorFull() {
        when(client.verifyMatch(any())).thenThrow(new RejectedExecutionException("queue full"));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "executor_full").count()).isEqualTo(1.0);
    }

    @Test
    void unexpected_classifiedAsUnexpectedAtErrorLevel() {
        when(client.verifyMatch(any())).thenThrow(new IllegalStateException("boom"));

        svc.verifyAsync(matchId, "l", "f");

        assertThat(meters.counter("matching.verify.requests_total",
                "result", "error", "reason", "unexpected").count()).isEqualTo(1.0);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :matching-service:test --tests '*MatchVerificationServiceTest*'
```

Expected: FAIL — `MatchVerificationService` doesn't exist.

- [ ] **Step 3: Implement `MatchVerificationService`**

Create `services/matching-service/src/main/java/com/foundflow/matching/service/MatchVerificationService.java`:

```java
package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.FoundItemDescription;
import com.foundflow.genai.client.model.LostItemDescription;
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import com.foundflow.matching.config.GenaiVerifyProperties;
import com.foundflow.matching.domain.MatchVerification;
import com.foundflow.matching.repository.MatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
public class MatchVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MatchVerificationService.class);

    private final GenaiClient client;
    private final MatchRepository repo;
    private final MeterRegistry meters;
    private final GenaiVerifyProperties props;

    public MatchVerificationService(GenaiClient client,
                                    MatchRepository repo,
                                    MeterRegistry meters,
                                    GenaiVerifyProperties props) {
        this.client = client;
        this.repo = repo;
        this.meters = meters;
        this.props = props;
    }

    @Async("genaiVerifyExecutor")
    public void verifyAsync(UUID matchId, String lostText, String foundText) {
        if (!props.isEnabled()) {
            meters.counter("matching.verify.requests_total", "result", "disabled").increment();
            return;
        }

        Timer.Sample sample = Timer.start(meters);
        try {
            VerifyMatchRequest req = new VerifyMatchRequest()
                    .lost(new LostItemDescription().description(lostText))
                    .found(new FoundItemDescription().description(foundText));

            VerifyMatchResponse resp = client.verifyMatch(req);
            String verdict = resp.getVerdict().getValue();

            repo.applyVerification(matchId, new MatchVerification(
                    verdict,
                    resp.getConfidence(),
                    resp.getRationale(),
                    resp.getModelInfo() != null ? resp.getModelInfo().getProvider() : null,
                    resp.getModelInfo() != null ? resp.getModelInfo().getModel() : null,
                    OffsetDateTime.now()
            ));

            meters.counter("matching.verify.requests_total", "result", "success").increment();
            meters.counter("matching.verify.verdict_total", "verdict", verdict).increment();
            if (resp.getConfidence() != null) {
                meters.summary("matching.verify.confidence").record(resp.getConfidence());
            }
        } catch (Exception e) {
            String reason = classify(e);
            meters.counter("matching.verify.requests_total",
                    "result", "error", "reason", reason).increment();
            if ("contract_error".equals(reason) || "unexpected".equals(reason)) {
                log.error("verify-match failed for match {} ({}): {}",
                        matchId, reason, e.getMessage(), e);
            } else {
                log.warn("verify-match failed for match {} ({}): {}",
                        matchId, reason, e.getMessage());
            }
        } finally {
            sample.stop(meters.timer("matching.verify.duration"));
        }
    }

    static String classify(Throwable t) {
        if (t instanceof HttpServerErrorException.GatewayTimeout) return "timeout";
        if (t instanceof HttpServerErrorException) return "upstream_5xx";
        if (t instanceof HttpClientErrorException.TooManyRequests) return "throttled";
        if (t instanceof HttpClientErrorException) return "contract_error";
        if (t instanceof RejectedExecutionException) return "executor_full";
        if (t instanceof java.net.SocketTimeoutException
                || t instanceof org.springframework.web.client.ResourceAccessException) return "timeout";
        return "unexpected";
    }
}
```

**Note on generated model accessors.** As in Task 6, the exact accessors depend on `openApiGenerate` config. Replace `.getVerdict().getValue()`, `.lost(...)`, `.description(...)`, `.getModelInfo().getProvider()` with whatever the generator emits.

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :matching-service:test --tests '*MatchVerificationServiceTest*'
```

Expected: PASS — all 8 tests.

- [ ] **Step 5: Commit**

```bash
git add services/matching-service/src/main/java/com/foundflow/matching/service/MatchVerificationService.java \
        services/matching-service/src/test/java/com/foundflow/matching/service/MatchVerificationServiceTest.java
git commit -m "feat(matching): MatchVerificationService — async /verify-match with metrics

Refs #151."
```

---

## Task 9: Wire `verifyAsync` into `processIntake` + non-blocking slice test

**Files:**
- Modify: `services/matching-service/src/main/java/com/foundflow/matching/service/CandidateMatchingService.java`
- Modify: `services/matching-service/src/test/java/com/foundflow/matching/service/CandidateMatchingServiceTest.java`
- Create: `services/matching-service/src/test/java/com/foundflow/matching/service/ProcessIntakeAsyncSliceIT.java`

- [ ] **Step 1: Update `CandidateMatchingServiceTest` to expect the new call**

Open the existing `CandidateMatchingServiceTest`. Find the test that exercises the above-threshold publish path and add a verification:

```java
// after: verify(eventPublisher).publishMatchCandidateCreated(persistedMatch);
verify(verificationService).verifyAsync(
        eq(persistedMatch.getId()), anyString(), anyString());
```

You will need to add `MatchVerificationService verificationService = mock(MatchVerificationService.class);` to the test's setUp and inject it into the `CandidateMatchingService` constructor (after Step 3 below).

- [ ] **Step 2: Run to verify the test fails**

```bash
./gradlew :matching-service:test --tests '*CandidateMatchingServiceTest*'
```

Expected: FAIL — the constructor signature doesn't include `MatchVerificationService` yet.

- [ ] **Step 3: Wire `MatchVerificationService` into `CandidateMatchingService`**

In `services/matching-service/src/main/java/com/foundflow/matching/service/CandidateMatchingService.java`:

a) Add the field and constructor parameter:

```java
private final MatchVerificationService verificationService;

// in the constructor parameter list, add:
MatchVerificationService verificationService,

// in the constructor body, add:
this.verificationService = verificationService;
```

b) Inside the candidate loop, **immediately after** `eventPublisher.publishMatchCandidateCreated(persisted);`, add:

```java
String ownText = embeddingText;             // already in scope earlier in processIntake
String otherText = candidate.textSource();  // available from Task 5
String lostText = itemType == ItemType.LOST ? ownText : otherText;
String foundText = itemType == ItemType.LOST ? otherText : ownText;
verificationService.verifyAsync(persisted.getId(), lostText, foundText);
```

- [ ] **Step 4: Run the unit test to verify it passes**

```bash
./gradlew :matching-service:test --tests '*CandidateMatchingServiceTest*'
```

Expected: PASS.

- [ ] **Step 5: Write the failing non-blocking slice IT**

Create `services/matching-service/src/test/java/com/foundflow/matching/service/ProcessIntakeAsyncSliceIT.java`:

```java
package com.foundflow.matching.service;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.*;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class ProcessIntakeAsyncSliceIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean GenaiClient genaiClient;
    @Autowired MatchRepository matchRepository;
    @Autowired CandidateMatchingService matchingService;

    @Test
    void slowVerify_doesNotBlockProcessIntakeReturning() throws Exception {
        // Arrange: stub embed to return some vector; stub verifyMatch to sleep 8s before returning
        when(genaiClient.embed(any())).thenReturn(new float[768]);
        when(genaiClient.verifyMatch(any())).thenAnswer(inv -> {
            Thread.sleep(Duration.ofSeconds(8).toMillis());
            VerifyMatchResponse r = new VerifyMatchResponse();
            r.setVerdict(VerifyMatchResponse.VerdictEnum.fromValue("match"));
            r.setConfidence(0.9f);
            r.setRationale("slow but ok");
            return r;
        });

        // Arrange a counterpart embedding in the DB so a candidate is found above threshold.
        // [Test must seed item_embeddings with a matching opposite-type row in venue X.
        //  Use the existing test helpers if any, or itemEmbeddingRepository.upsert(...).]

        long t0 = System.nanoTime();
        matchingService.processIntake(
                /* itemType */ null /* fill in via test helper */,
                UUID.randomUUID(), UUID.randomUUID(),
                "blue jacket lobby", null);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        // processIntake must return well before the 8s sleep in verifyMatch
        assertThat(elapsedMs).isLessThan(2000);

        // verify_* fields land within the verify timeout window
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() -> {
            Match m = matchRepository.findAll().iterator().next();
            assertThat(m.getVerifyVerdict()).isEqualTo("match");
        });
    }
}
```

**Note.** The arrange-step that seeds a counterpart embedding is sketched; the implementer fills in based on `ItemEmbeddingRepositoryIT`'s existing arrangement pattern. The assertion intent — `elapsedMs < 2000` while verifyMatch sleeps for 8000 — is the load-bearing check.

- [ ] **Step 6: Run to verify the slice test passes**

```bash
./gradlew :matching-service:test --tests '*ProcessIntakeAsyncSliceIT*'
```

Expected: PASS — `processIntake` returns immediately (<2s) and the verdict materialises within 12s.

- [ ] **Step 7: Run the full matching-service test suite**

```bash
./gradlew :matching-service:check
```

Expected: PASS, including ArchUnit + Flyway migrations + existing tests.

- [ ] **Step 8: Commit**

```bash
git add services/matching-service/src/main/java/com/foundflow/matching/service/CandidateMatchingService.java \
        services/matching-service/src/test/java/com/foundflow/matching/service/CandidateMatchingServiceTest.java \
        services/matching-service/src/test/java/com/foundflow/matching/service/ProcessIntakeAsyncSliceIT.java
git commit -m "feat(matching): fire verify-match async after publishing MatchCandidateCreated

Refs #151."
```

---

## Task 10: Final verification + PR

- [ ] **Step 1: Run the whole matching-service + shared/genai-client suites once more**

```bash
./gradlew :matching-service:check
./gradlew -p shared/genai-client check
```

Expected: PASS on both.

- [ ] **Step 2: Check the spec file is also staged**

The spec was left uncommitted on `development` (per the user decision in brainstorming). Stage and commit it onto this branch so the PR carries the spec alongside the implementation:

```bash
git add docs/superpowers/specs/2026-05-30-verify-match-wiring-design.md \
        docs/superpowers/plans/2026-05-30-verify-match-wiring-implementation.md
git commit -m "docs(plans): verify-match wiring spec + implementation plan

Refs #151."
```

- [ ] **Step 3: Push the branch**

```bash
git push -u origin feat/151-verify-match-wiring
```

- [ ] **Step 4: Open the PR**

```bash
gh pr create --repo AET-DevOps26/team-chaos-monkeys \
  --base development \
  --title "feat(matching): wire genai-service /verify-match into matching pipeline" \
  --body "$(cat <<'EOF'
## Summary
- New Flyway V5 migration adds `verify_*` columns to `match` table with a CHECK constraint on the verdict enum.
- Extends `shared/genai-client` with `verifyMatch(...)` plus WireMock tests.
- Adds `MatchVerificationService` in `matching-service` that calls `/verify-match` on a dedicated `@Async` executor immediately after `MatchCandidateCreated.v1` is published — fire-and-forget, never blocks intake.
- Persists verdict, confidence, rationale, model provenance, and completion timestamp on the `Match` row.
- Classifies all failure modes (`timeout`, `upstream_5xx`, `throttled`, `contract_error`, `executor_full`, `unexpected`) and emits Micrometer metrics.
- Kill-switch via `genai.verify.enabled`.
- ADR 0001 in `services/matching-service/docs/adr/` locks the decisions; full design in `docs/superpowers/specs/2026-05-30-verify-match-wiring-design.md`.

Closes #151.

## Test plan
- [x] `./gradlew :matching-service:check` — all unit + IT tests pass
- [x] `./gradlew -p shared/genai-client check` — WireMock tests pass
- [x] `ProcessIntakeAsyncSliceIT` proves the LLM call does not block the listener thread
- [ ] Manual smoke (post-merge, once #167's embed migration is live): hit `POST /api/matches/{n}` via gateway, observe `verify_*` columns populate on the `match` row, observe `matching.verify.*` metrics at `/actuator/prometheus`

## Out of scope (follow-ups)
- Grafana panels for the new metrics — belongs in the matching-dashboard ticket.
- E2E test through `tests/e2e/foundflow-e2e.ps1` — requires RabbitMQ in compose first.
- Score-band gate (`genai.verify.score-band`) — needs distribution data from Grafana.
- `MatchCandidateVerified.v1` event — wait for a consumer that needs push semantics.
- Bounded retry with jitter — only if the failure-rate dashboard shows we need it.
- Sentry capture for `contract_error` / `unexpected` — wait for the observability decision in #91.
EOF
)"
```

- [ ] **Step 5: Verify the PR opened cleanly**

```bash
gh pr view --repo AET-DevOps26/team-chaos-monkeys --json url,state,checks
```

Expected: `state: OPEN`, CI starts. Watch the CI run; if `backend-tests` matrix for matching-service goes green, hand off to review.

---

## Self-review notes

- **Spec coverage.** Every decision in the spec maps to at least one task: integration point (T9), async (T7+T8+T9), persistence (T3+T4), shared-client extension (T6), failure modes + metrics (T8), kill-switch (T7+T8), event payload unchanged (no task — it's the absence of a change), ADR (T1).
- **Type consistency.** `verifyAsync(UUID, String, String)` signature is consistent across T8 (definition), T9 (call site), and the test in T8. `MatchVerification` record fields are consistent across T4 (definition), T8 (construction), and the repo query in T4.
- **Placeholder reminders left in the plan.** Two "Note on generated model accessors" callouts (T6, T8) tell the implementer to adapt openapi-generator-emitted accessor names — this is necessary because the exact names depend on local generator config the plan can't predict.
- **#167 dependency.** T1 step 2 handles both possible states (already-added or needs-adding). The plan does not block on #167 merging first.
- **No Sentry SDK in matching-service today.** The spec's "Sentry: Yes" entries are realised as `log.error(...)`; promotion noted in T1's ADR and in the PR description's follow-ups.
