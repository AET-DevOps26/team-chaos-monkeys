# genai-service

Stateless Python 3.12 + FastAPI service that powers attribute extraction, embeddings, and match verification for FoundFlow. See [system architecture](../../docs/architecture/system-architecture.md) for how it fits with the Spring services and `matching-service`.

## Provider configuration

The service speaks to one LLM provider, picked at startup via `GENAI_PROVIDER`. The same code path (`app/providers/`) serves both — switching providers is a config change, never a code change.

| Env var | Required | Default | Notes |
|---|---|---|---|
| `GENAI_PROVIDER` | yes | `openai` in root `.env.example` | `openai`, `local` (Ollama), or `fake`. Invalid values fail at startup. |
| `OPENAI_API_KEY` | iff `provider=openai` | — | Validated at startup; missing key crashloops the container. |
| `OPENAI_CHAT_MODEL` | no | `gpt-4o-mini` | |
| `OPENAI_EMBED_MODEL` | no | `text-embedding-3-small` | |
| `OLLAMA_BASE_URL` | no | `http://ollama:11434` | Override for non-compose dev. |
| `OLLAMA_CHAT_MODEL` | no | `llama3.2:3b` | Pulled by the `ollama-init` sidecar. |
| `OLLAMA_EMBED_MODEL` | no | `nomic-embed-text` | Pulled by the `ollama-init` sidecar. |
| `GENAI_TIMEOUT_SECONDS` | no | `30` | Per-request timeout for chat + embed. |

The Compose stack defaults to `GENAI_PROVIDER=openai`. Copy the root `.env.example` to `.env`, then set `OPENAI_API_KEY` from the shared Bitwarden entry. The service validates the key configuration and probes the embedding model during startup.

## Run locally

### Via docker-compose (recommended)

```bash
cp .env.example .env
# Set OPENAI_API_KEY in .env from the shared Bitwarden entry
docker compose up --build
```

Verify with:

```bash
curl http://localhost:8000/_diagnostic | jq
# { "provider": "openai", "chat_ok": true, "embed_ok": true, ... }
```

### Optional local Ollama provider

To run without an OpenAI key, set `GENAI_PROVIDER=local` in the root `.env` and enable the optional Compose profile:

```bash
docker compose --profile ollama up --build
```

The first run pulls the Ollama image and configured chat, vision, and embedding models. They are cached in the `ollama-models` volume for subsequent runs.

### Standalone (host Python)

Requires Python 3.12.

```bash
cd services/genai-service
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
export GENAI_PROVIDER=openai
export OPENAI_API_KEY=sk-...
uvicorn app.main:app --reload --port 8000
```

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /extract-attributes` | Extract structured `ItemAttributes` from a lost-item description, photo, or both. Single-item only. At least one of `description` or `image` is required; both is supported with per-field reconciliation (see ADR 0001). |
| `POST /embed` | Embed 1-32 texts into vectors for the matching-service. Stateless — vectors are returned, never stored. |
| `POST /verify-match` | Verify and explain whether a lost report and a candidate found item are the same item. |
| `POST /answer` | Generate a grounded, cited answer over retrieved item snippets (staff semantic search, #178). |
| `GET /health` | Liveness probe |
| `GET /_diagnostic` | Exercises chat + embed against the configured provider — useful for verifying credentials and connectivity. **Not** part of the public OpenAPI contract; excluded from generated SDKs. |
| `GET /metrics` | Prometheus exposition. See [Metrics](#metrics). Excluded from the OpenAPI schema. |
| `GET /docs` | Swagger UI |

All public endpoints are specified in `api/openapi.yaml`, the single source of truth.

## Limitations & failure modes

The service is stateless — no persistence, no caching, no queues. Vectors and verdicts are returned to the caller; nothing is stored here.

**Error envelope.** All non-2xx responses use the flat `{code, message, details?}` envelope declared in `api/openapi.yaml`:

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body fails Pydantic validation. For image input, `details.reason` discriminates: `at_least_one_required`, `image_base64_invalid`, `image_mime_unsupported`, `image_too_large`, `image_decode_failed`. |
| 413 | `PAYLOAD_TOO_LARGE` | HTTP body exceeded the 8 MiB cap. Enforced at the middleware layer before Pydantic — caller should downscale and retry. |
| 422 | `MODEL_OUTPUT_INVALID` | The provider call succeeded but the model's output was malformed JSON or failed schema validation. `details` carries `rawOutput` and `schemaErrors`. Callers should retry once before falling back. |
| 429 | `PROVIDER_RATE_LIMITED` | Upstream provider throttled the request. |
| 500 | `INTERNAL_ERROR` | Server fault (unknown/misconfigured model, uncaught exception). Message is generic; detail goes to logs only. |
| 502 | `PROVIDER_UNAVAILABLE` | Upstream provider is unreachable or returned 5xx. |
| 504 | `PROVIDER_TIMEOUT` | Upstream provider exceeded `GENAI_TIMEOUT_SECONDS`. |

**Known limitations:**

- **Single item per extraction.** `/extract-attributes` returns one `ItemAttributes`; multi-item guest descriptions are not detected or split.
- **No auth.** Endpoints are unauthenticated — the service is intended for in-cluster callers only.
- **No streaming.** Responses are buffered until the model finishes.
- **One provider per process.** Switching `GENAI_PROVIDER` requires a restart.
- **Image input is JPEG / PNG / WebP only**, base64-encoded in the request body, with a 5 MiB decoded cap (8 MiB HTTP body cap). Convert HEIC upstream — the photo-storage abstraction (#67) is the right place. The image pipeline downscales to ≤ 1024 px and strips EXIF before forwarding to the provider.
- **Local-provider vision uses `OLLAMA_VISION_MODEL` (default `llava:7b`)** — see ADR 0001 §7. CPU latency is ~15-30 s per call; the demo path is `GENAI_PROVIDER=openai`. NSFW content filtering exists on the OpenAI path (provider-side) but **not** on the local llava path.
- **Provider parity is contract-only.** Both adapters honour the same `LLMProvider` protocol, but model quality differs — the default local `llama3.2:3b` is markedly weaker at structured extraction than `gpt-4o-mini`. The golden regression catches drift over time; absolute quality is a model-choice problem, not a code problem.
- **Fail-fast on misconfig.** An invalid `GENAI_PROVIDER`, or a missing `OPENAI_API_KEY` when `provider=openai`, crashloops the container at startup — there is no silent fallback to the other provider.

## Metrics

`GET /metrics` exposes the Prometheus exposition for the service. The shared infra Prometheus already scrapes it; for ad-hoc inspection:

```
curl -s http://localhost:8000/metrics | grep -E '^(http_|genai_)'
```

Two layers of metrics are exposed:

**HTTP-level** — wired by `prometheus-fastapi-instrumentator`, standard names:

| Metric | Type | Labels |
|---|---|---|
| `http_requests_total` | counter | `handler`, `method`, `status` (`2xx`/`4xx`/`5xx`) |
| `http_request_duration_seconds` | histogram | `handler`, `method` |
| `http_request_size_bytes`, `http_response_size_bytes` | summary | `handler` |

**GenAI-specific** — defined in `app/metrics.py`:

| Metric | Type | Labels | What it tells you |
|---|---|---|---|
| `genai_provider_requests_total` | counter | `provider`, `endpoint`, `outcome` | Provider invocations by outcome: `ok`, `timeout`, `rate_limit`, `unavailable`, `bad_request`, `error`. Drives the provider health panel. |
| `genai_provider_request_duration_seconds` | histogram | `provider`, `endpoint` | Latency of the provider call itself, not the full HTTP request. |
| `genai_validation_errors_total` | counter | `endpoint`, `reason` | Structured-output parse failures by reason: `json_decode`, `wrong_type`, `schema`. Surfaces `ModelOutputError` rate without tailing logs. |
| `genai_build_info` | gauge | `provider` | Set once at startup so dashboards can filter panels by deployment. |

`provider` is the value of `GENAI_PROVIDER` (`openai` / `local`). `endpoint` is one of `extract-attributes`, `embed`, `verify-match`. A parse failure does *not* count as a provider failure: the provider call succeeded, the output was just unusable — the validation counter takes care of surfacing it.

## Tests

```
pip install -e '.[dev]'
pytest                      # unit + contract tests (no network, no Ollama)
GENAI_RUN_INTEGRATION=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \
    pytest tests/integration/test_real_openai_provider.py   # hits the real OpenAI API
```

The contract test (`tests/test_provider_contract.py`) parametrizes the same assertions over both adapters using `respx`. If a future change drifts one adapter's behavior from the other (e.g. silently swallows timeouts), this test fails first.

### Golden extraction regression

`tests/golden/` holds a 22-case attribute-extraction set. Normal CI runs it as a wiring check (every description must flow through `/extract-attributes`) and unit-tests the fuzzy comparator. The real-LLM quality regression is gated so it does not run in CI:

```
GENAI_RUN_GOLDEN=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \
    pytest tests/integration/test_golden_extraction.py -s
```

It runs one extraction per case, scores each field fuzzily against the hand-authored expectations, prints a per-case report, and asserts loose aggregate floors — enough to catch a prompt change that tanks extraction quality.

## Layout

```
app/
  main.py            FastAPI app, lifespan, router wiring
  config.py          Settings (pydantic-settings) + validators
  errors.py          Contract error envelope + exception handlers
  exceptions.py      LLMError hierarchy + ModelOutputError
  extraction.py      Attribute-extraction prompt + output validation
  verification.py    Match-verification prompt + output validation
  embedding.py       Text-embedding batch fan-out
  metrics.py         Prometheus metric definitions + observe_provider_call
  dependencies.py    FastAPI dependencies: get_llm(), get_settings()
  api/
    health.py        /health
    extract.py       POST /extract-attributes
    embed.py         POST /embed
    verify.py        POST /verify-match
    diagnostic.py    /_diagnostic (internal)
    schemas.py       Pydantic models mirroring api/openapi.yaml
  providers/
    __init__.py      LLMProvider protocol + build_provider() factory
    openai.py        OpenAI adapter
    ollama.py        Ollama adapter
    fake.py          FakeProvider for tests
tests/
  test_settings.py
  test_provider_contract.py     parametrized over both adapters
  test_extraction.py            extraction logic (mocked provider)
  test_extract_attributes.py    /extract-attributes endpoint
  test_embedding.py             embedding logic (mocked provider)
  test_embed.py                 /embed endpoint
  test_golden_compare.py        golden-set fuzzy comparator
  test_diagnostic.py
  test_health.py
  test_metrics.py               /metrics endpoint + counter/histogram coverage
  golden/                       22-case attribute-extraction set
  integration/                  env-gated, hits a real provider
Dockerfile           python:3.12-slim, non-root user, uvicorn entrypoint
pyproject.toml       PEP 621 metadata + dev extras
```
