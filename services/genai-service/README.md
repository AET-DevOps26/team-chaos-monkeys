# genai-service

Stateless Python 3.12 + FastAPI service that powers attribute extraction, embeddings, and notification text generation for FoundFlow. See `docs/architecture.md` for how it fits with the Spring services and `matching-service`.

Endpoints beyond `/health` and `/_diagnostic` are added under follow-up tickets (#52 output validation, #54 metrics).

## Provider configuration

The service speaks to one LLM provider, picked at startup via `GENAI_PROVIDER`. The same code path (`app/providers/`) serves both — switching providers is a config change, never a code change.

| Env var | Required | Default | Notes |
|---|---|---|---|
| `GENAI_PROVIDER` | yes | — | `local` (Ollama) or `openai`. Invalid values fail at startup. |
| `OPENAI_API_KEY` | iff `provider=openai` | — | Validated at startup; missing key crashloops the container. |
| `OPENAI_CHAT_MODEL` | no | `gpt-4o-mini` | |
| `OPENAI_EMBED_MODEL` | no | `text-embedding-3-small` | |
| `OLLAMA_BASE_URL` | no | `http://ollama:11434` | Override for non-compose dev. |
| `OLLAMA_CHAT_MODEL` | no | `llama3.2:3b` | Pulled by the `ollama-init` sidecar. |
| `OLLAMA_EMBED_MODEL` | no | `nomic-embed-text` | Pulled by the `ollama-init` sidecar. |
| `GENAI_TIMEOUT_SECONDS` | no | `30` | Per-request timeout for chat + embed. |

The compose stack defaults to `GENAI_PROVIDER=local`, so a fresh `docker compose up` runs end-to-end without an API key. To use OpenAI in local dev: copy `.env.example` to `.env`, set `GENAI_PROVIDER=openai` and `OPENAI_API_KEY=…`.

## Run locally

### Via docker-compose (recommended)

```
docker compose up
```

First run pulls ~1.5 GB of Ollama models (`llama3.2:3b` + `nomic-embed-text`) via the `ollama-init` sidecar. This takes 3-5 minutes depending on your connection. Models are cached in the `ollama-models` volume, so subsequent runs are instant.

Verify with:

```
curl http://localhost:8000/_diagnostic | jq
# { "provider": "local", "chat_ok": true, "embed_ok": true, ... }
```

### Standalone (host Python)

Requires Python 3.12.

```
cd services/genai-service
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
export GENAI_PROVIDER=local
export OLLAMA_BASE_URL=http://localhost:11434   # if Ollama runs on the host
uvicorn app.main:app --reload --port 8000
```

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /extract-attributes` | Extract structured `ItemAttributes` from a free-text lost-item description. Single-item only — multi-item descriptions are tracked separately (#88). |
| `POST /embed` | Embed 1-32 texts into vectors for the matching-service. Stateless — vectors are returned, never stored. |
| `POST /generate-message` | Generate guest pickup notification text for a confirmed match. Best-effort — `notification-service` falls back to a static template on failure. |
| `GET /health` | Liveness probe |
| `GET /_diagnostic` | Exercises chat + embed against the configured provider — useful for verifying credentials and connectivity. **Not** part of the public OpenAPI contract; excluded from generated SDKs. |
| `GET /docs` | Swagger UI |

All public endpoints are specified in `api/openapi.yaml`, the single source of truth.

## Tests

```
pip install -e '.[dev]'
pytest                      # unit + contract tests (no network, no Ollama)
GENAI_RUN_INTEGRATION=1 pytest tests/integration/test_real_provider.py   # hits real Ollama
```

The contract test (`tests/test_provider_contract.py`) parametrizes the same assertions over both adapters using `respx`. If a future change drifts one adapter's behavior from the other (e.g. silently swallows timeouts), this test fails first.

### Golden extraction regression

`tests/golden/` holds a 22-case attribute-extraction set. Normal CI runs it as a wiring check (every description must flow through `/extract-attributes`) and unit-tests the fuzzy comparator. The real-LLM quality regression is gated so it does not run in CI:

```
GENAI_RUN_GOLDEN=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \
    pytest tests/integration/test_golden_extraction.py -s
```

It runs one extraction per case, scores each field fuzzily against the hand-authored expectations, prints a per-case report, and asserts loose aggregate floors — enough to catch a prompt change that tanks extraction quality.

### Generation check

`tests/integration/test_real_generation.py` is a gated real-LLM check that `/generate-message`'s prompt yields schema-valid output on a real provider. Like the golden regression it is excluded from CI; run it on demand:

```
GENAI_RUN_GENERATION=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \
    pytest tests/integration/test_real_generation.py -s
```

It makes one call per (language, tone) case and asserts the output parses into the response schema and carries the supplied case reference — a structural check, not a prose-quality score.

## Layout

```
app/
  main.py            FastAPI app, lifespan, router wiring
  config.py          Settings (pydantic-settings) + validators
  errors.py          Contract error envelope + exception handlers
  exceptions.py      LLMError hierarchy + ModelOutputError
  extraction.py      Attribute-extraction prompt + output validation
  embedding.py       Text-embedding batch fan-out
  dependencies.py    FastAPI dependencies: get_llm(), get_settings()
  api/
    health.py        /health
    extract.py       POST /extract-attributes
    embed.py         POST /embed
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
  golden/                       22-case attribute-extraction set
  integration/                  env-gated, hits a real provider
Dockerfile           python:3.12-slim, non-root user, uvicorn entrypoint
pyproject.toml       PEP 621 metadata + dev extras
```
