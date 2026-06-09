$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceRoots = @(
    "services/auth-service",
    "services/gateway-service",
    "services/lost-item-service",
    "services/found-item-service",
    "services/matching-service",
    "services/notification-service",
    "services/operations-service",
    "services/pickup-service"
)

$results = @()

foreach ($serviceRoot in $serviceRoots) {
    $fullPath = Join-Path $repoRoot $serviceRoot
    Write-Host "=== Running tests in $serviceRoot ==="

    if (-not (Test-Path $fullPath)) {
        Write-Host "FAILED: $serviceRoot (directory not found: $fullPath)"
        $results += [pscustomobject]@{
            Service = $serviceRoot
            ExitCode = 1
            Success = $false
        }
        continue
    }

    Push-Location $fullPath
    try {
        $exitCode = 1
        & .\gradlew test
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $results += [pscustomobject]@{
        Service = $serviceRoot
        ExitCode = $exitCode
        Success = ($exitCode -eq 0)
    }

    if ($exitCode -ne 0) {
        Write-Host "FAILED: $serviceRoot (exit code $exitCode)"
    }
    else {
        Write-Host "OK: $serviceRoot"
    }
}

$failedResults = $results | Where-Object { -not $_.Success }

Write-Host ""
Write-Host "=== Overall result ==="

if ($failedResults.Count -gt 0) {
    Write-Host "Some service test runs failed:"
    $failedResults | ForEach-Object {
        Write-Host "- $($_.Service): exit code $($_.ExitCode)"
    }
    exit 1
}

Write-Host "All service test runs passed."
exit 0