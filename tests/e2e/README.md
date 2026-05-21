# FoundFlow E2E Tests

Run these tests against a running local Docker Compose stack.

```powershell
docker compose up --build -d
.\tests\e2e\foundflow-e2e.ps1
```

The same script is run by GitHub Actions in `.github/workflows/ci.yml` after the backend stack has been built and started with Docker Compose.

The script covers:

- gateway health and unauthenticated `401`
- public lost-item reporting
- JSON token login, refresh, and logout via `/api/auth`
- admin user and KPI access
- OPS_MANAGER own-venue user management
- same-venue matching
- cross-venue matching rejection
- venue KPI aggregation

Defaults:

```powershell
.\tests\e2e\foundflow-e2e.ps1 `
  -GatewayBaseUrl http://localhost:8080 `
  -AdminEmail admin@foundflow.local `
  -AdminPassword admin12345
```
