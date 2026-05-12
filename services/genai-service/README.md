# genai-service

Stateless Python 3.12 + FastAPI service that powers attribute extraction, embeddings, and notification text generation for FoundFlow. See `docs/architecture.md` for how it fits with the Spring services and `matching-service`.

Endpoints beyond `/health` are added under follow-up tickets (#48 contracts, #49 extraction, #50 embeddings, #51 provider switch, #52 output validation, #53 notification text, #54 metrics).

## Run locally

Requires Python 3.12.

```
cd services/genai-service
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
uvicorn app.main:app --reload --port 8000
```

Then:

```
curl http://localhost:8000/health
# {"status":"ok","service":"genai-service","version":"0.1.0"}
```

Swagger UI: <http://localhost:8000/docs>.

## Run in Docker

```
docker build -t foundflow/genai-service:dev services/genai-service
docker run --rm -p 8000:8000 foundflow/genai-service:dev
curl http://localhost:8000/health
```

## Tests

```
pip install -e '.[dev]'
pytest
```

## Layout

```
app/
  main.py        FastAPI app, router registration
  api/
    health.py    /health endpoint
    schemas.py   placeholder request/response models for upcoming endpoints
tests/
  test_health.py smoke test for /health
Dockerfile       python:3.12-slim, non-root user, uvicorn entrypoint
pyproject.toml   PEP 621 metadata + dev extras (pytest, httpx)
```
