# ADR 0002 — Keep REST/JSON transport and the in-house provider abstraction (no gRPC, no LangChain)

- **Status**: Accepted
- **Date**: 2026-06-22
- **Issue**: [#232](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/232)
- **Subsystem owner**: Luca Kollmer
- **Tags**: genai-service, transport, architecture, openapi

## Context

A lecture presented gRPC and LangChain as part of a typical LLM service stack. Rather than adopt or reject either by default, we evaluated both deliberately so the choice can be defended in the oral exam. Two independent questions:

1. **gRPC** as the transport between the Spring services and `genai-service`, replacing/augmenting REST/JSON over HTTP.
2. **LangChain** inside `genai-service` for the extract / embed / verify operations.

This ADR records that we adopt **neither**, and why. Both are reversible decisions — nothing here precludes revisiting them if the constraints below change.

## Decisions

### 1. Transport stays REST/JSON over HTTP — do not adopt gRPC

**Decision.** The Spring services keep calling `genai-service` over REST/JSON. No protobuf contracts, no gRPC stack.

**Rationale.**
- **Contract surface.** The course requires OpenAPI/Swagger docs, and our whole contract story is REST: springdoc per service, gateway Swagger aggregation, `api/openapi.yaml` for genai, and the Orval-generated frontend client. gRPC isn't browser-native and would carve genai out of that documented surface.
- **No bottleneck to fix.** The genai calls are low-frequency and best-effort: intake swallows extraction failures, and verify-match runs async off the request path (see matching-service ADR 0001). gRPC's wins — streaming, multiplexed low-latency RPC — don't address anything we're constrained by.
- **Cost outweighs benefit.** Adopting gRPC means protobuf contracts plus codegen in *both* the Gradle (Java) and Python pipelines, and extra moving parts in compose, Helm, and health checks — for a path that isn't latency-bound.

**Consequences.** We keep one documented contract surface. If a future high-throughput or streaming genai path appears (e.g. token streaming to the UI), gRPC can be reconsidered for that path specifically without disturbing the existing REST contracts.

### 2. genai-service keeps its in-house provider abstraction — do not adopt LangChain

**Decision.** `genai-service` continues to use its own provider interface (`app/providers/{openai,ollama,fake}.py`, selected via `GENAI_PROVIDER`) plus the OpenAI SDK and Pydantic directly. No LangChain.

**Rationale.**
- **Abstraction-on-abstraction.** We already have one clean interface across three providers. LangChain would wrap an abstraction we already own, adding a heavy dependency for indirection we don't need.
- **The `fake` provider is an asset.** The deterministic `fake` provider powers tests and E2E; LangChain complicates that path for no gain.
- **We already have its other pitches.** Structured output and retries come from the OpenAI SDK + Pydantic today. LangChain's orchestration/agent features have no use case in our genai operations, which are all single-shot (extract, embed, verify, and the grounded `/answer`).
- **RAG does not change this.** Staff semantic search (epic #177) is corpus-grounded RAG, but the topology deliberately splits the pieces: retrieval is venue-scoped kNN over pgvector *in matching-service* (Java — out of LangChain's reach), and generation is genai's stateless `/answer` endpoint (#178, shipped), a pure function that mirrors `/verify-match` and takes pre-retrieved snippets. LangChain's RAG value is orchestrating retrieve → generate → chain inside one runtime; we don't do that in one runtime, and `/answer` landed without it.

**Consequences.** The dependency surface stays small and the test story (especially `fake`) stays simple. The trigger to revisit is not "RAG" — we have that — but **agentic, multi-step orchestration inside genai**: iterative/agentic retrieval, query rewriting with re-retrieval, or multi-turn tool use. That shape is closer to the deferred analytics-chat epic (#109, text-to-SQL); if it lands, LangChain or LangGraph can be revisited then.

## References

- Issue [#232](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/232).
- `services/genai-service/docs/adr/0001-image-attribute-extraction.md` — provider abstraction and ADR format precedent.
- `services/matching-service/docs/adr/0001-verify-match-integration.md` — the async, best-effort verify path.
- Epic [#177](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/177) (staff semantic search / RAG) and [#178](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/178) (the stateless `/answer` endpoint) — the RAG topology referenced above. [#109](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/109) — the deferred analytics-chat epic.
- `api/README.md`, `api/openapi.yaml` — REST contract story.
