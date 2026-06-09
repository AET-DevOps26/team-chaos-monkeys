# FoundFlow E2E Tests

Run these tests against a running local Docker Compose stack.

```powershell
# Use the deterministic provider unless you intentionally start Ollama with
# `docker compose --profile ollama up`.
$env:GENAI_PROVIDER = "fake"
docker compose up --build -d
.\tests\e2e\foundflow-e2e.ps1
```

The same script is run by GitHub Actions in `.github/workflows/ci.yml` after the backend stack has been built and started with Docker Compose.

Triage note: if an old E2E run fails only at the post-confirmation check for
empty `PENDING` matches, re-run it before blaming the PR diff. The intake
pipeline can asynchronously auto-create a second same-pair `PENDING` match;
the current test asserts the known manual match is `CONFIRMED` instead.

The script covers:

- gateway health and unauthenticated `401`
- public Swagger/OpenAPI and proxied health endpoints through the gateway
- public lost-item reporting
- multipart lost/found item photo upload through the services
- photo retrieval, signed photo URLs, public photo access rejection, and dedicated photo replacement endpoints
- JSON lost/found item updates preserving the existing `photoKey`
- JSON token login, refresh, and logout via `/api/auth`
- refresh-token revocation after logout
- password reset via auth-issued token and notification-service event/mail row
- admin user lookup and KPI access
- OPS_MANAGER own-venue user management
- STAFF self get/update/delete and rejection for other users
- found/lost/match count and histogram endpoints
- same-venue matching
- cross-venue matching rejection
- notification CRUD and email filtering
- notification blueprint reads with authenticated users and role-gated writes
- venue KPI aggregation

Defaults:

```powershell
.\tests\e2e\foundflow-e2e.ps1 `
  -GatewayBaseUrl http://localhost:8080 `
  -AdminEmail admin@foundflow.local `
  -AdminPassword admin12345
```
