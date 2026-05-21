param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@foundflow.local",
    [string]$AdminPassword = "admin12345"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

function Assert-Status {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [int]$Expected,
        [string]$Label
    )

    $actual = [int]$Response.StatusCode
    if ($actual -ne $Expected) {
        $body = ""
        if ($null -ne $Response.Content) {
            $body = $Response.Content.ReadAsStringAsync().Result
        }
        throw "$Label expected HTTP $Expected but got $actual. Body: $body"
    }

    Write-Host "[OK] $Label -> HTTP $actual"
}

function Get-TokenPair {
    param(
        [string]$Username,
        [string]$Password
    )

    $client = [System.Net.Http.HttpClient]::new()
    $tokenResponse = $client.PostAsync("$GatewayBaseUrl/api/auth/login", (JsonContent @{
        email = $Username
        password = $Password
    })).Result
    $tokenBody = $tokenResponse.Content.ReadAsStringAsync().Result

    if (-not $tokenResponse.IsSuccessStatusCode) {
        throw "Login failed for $Username. Body: $tokenBody"
    }

    $tokens = $tokenBody | ConvertFrom-Json
    if ($tokens.expiresIn -ne 1800) {
        throw "Login for $Username should return a 30 minute access token. Expected expiresIn 1800 but got $($tokens.expiresIn)."
    }

    return $tokens
}

function Refresh-TokenPair {
    param([string]$RefreshToken)

    $client = [System.Net.Http.HttpClient]::new()
    $response = $client.PostAsync("$GatewayBaseUrl/api/auth/refresh", (JsonContent @{
        refreshToken = $RefreshToken
    })).Result
    $body = $response.Content.ReadAsStringAsync().Result

    if (-not $response.IsSuccessStatusCode) {
        throw "Refresh token request failed. Body: $body"
    }

    $tokens = $body | ConvertFrom-Json
    if ($tokens.expiresIn -ne 1800) {
        throw "Refresh should return a 30 minute access token. Expected expiresIn 1800 but got $($tokens.expiresIn)."
    }

    return $tokens
}

function Logout-RefreshToken {
    param([string]$RefreshToken)

    $client = [System.Net.Http.HttpClient]::new()
    $response = $client.PostAsync("$GatewayBaseUrl/api/auth/logout", (JsonContent @{
        refreshToken = $RefreshToken
    })).Result

    Assert-Status $response 204 "Refresh token can be logged out"
}

function New-GatewayClient {
    param([string]$AccessToken)

    $client = [System.Net.Http.HttpClient]::new()
    if ($AccessToken) {
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
            "Bearer",
            $AccessToken
        )
    }

    return $client
}

function JsonContent {
    param([object]$Body)

    return [System.Net.Http.StringContent]::new(
        ($Body | ConvertTo-Json -Depth 8),
        [Text.Encoding]::UTF8,
        "application/json"
    )
}

function Read-Json {
    param([System.Net.Http.HttpResponseMessage]$Response)

    $body = $Response.Content.ReadAsStringAsync().Result
    if ([string]::IsNullOrWhiteSpace($body)) {
        return $null
    }

    return $body | ConvertFrom-Json
}

function Post-Json {
    param(
        [System.Net.Http.HttpClient]$Client,
        [string]$Url,
        [object]$Body
    )

    return $Client.PostAsync($Url, (JsonContent $Body)).Result
}

Write-Host "Running FoundFlow E2E tests against $GatewayBaseUrl"

$publicClient = New-GatewayClient

$health = $publicClient.GetAsync("$GatewayBaseUrl/actuator/health").Result
Assert-Status $health 200 "Gateway health is public"

$protectedWithoutToken = $publicClient.GetAsync("$GatewayBaseUrl/api/venues").Result
Assert-Status $protectedWithoutToken 401 "Protected endpoint rejects missing token"

$publicVenueId = "11111111-1111-1111-1111-111111111111"
$publicLostReport = Post-Json $publicClient "$GatewayBaseUrl/api/lost-items" @{
    photoKey = "e2e-public-photo"
    description = "E2E public lost report"
    lostAt = "2026-05-19T15:30:00"
    location = "Gateway test"
    venueId = $publicVenueId
    contactEmail = "e2e-public@example.com"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E")
    }
}
Assert-Status $publicLostReport 201 "Public lost-item report can be created without token"

$adminTokens = Get-TokenPair $AdminEmail $AdminPassword
$adminTokens = Refresh-TokenPair $adminTokens.refreshToken
$adminClient = New-GatewayClient $adminTokens.accessToken

$users = $adminClient.GetAsync("$GatewayBaseUrl/api/users").Result
Assert-Status $users 200 "Admin can list users"

$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
$venueResponse = Post-Json $adminClient "$GatewayBaseUrl/api/venues" @{
    name = "E2E Venue $suffix"
    tone = "friendly"
    defaultLanguage = "de"
}
Assert-Status $venueResponse 201 "Admin can create venue"
$venue = Read-Json $venueResponse
$venueId = $venue.id

$opsEmail = "ops-$suffix@foundflow.local"
$opsPassword = "ops12345"
$opsResponse = Post-Json $adminClient "$GatewayBaseUrl/api/users" @{
    email = $opsEmail
    role = "OPS_MANAGER"
    password = $opsPassword
    venueId = $venueId
}
Assert-Status $opsResponse 200 "Admin can create OPS_MANAGER"
$opsUser = Read-Json $opsResponse

$opsTokens = Get-TokenPair $opsEmail $opsPassword
$opsClient = New-GatewayClient $opsTokens.accessToken

$opsUsers = $opsClient.GetAsync("$GatewayBaseUrl/api/users").Result
Assert-Status $opsUsers 200 "OPS_MANAGER can list own venue users"
$opsUsersBody = Read-Json $opsUsers
if (($opsUsersBody | Where-Object { $_.venueId -ne $venueId }).Count -gt 0) {
    throw "OPS_MANAGER user list contains users outside own venue."
}

$staffEmail = "staff-$suffix@foundflow.local"
$staffResponse = Post-Json $opsClient "$GatewayBaseUrl/api/users" @{
    email = $staffEmail
    role = "STAFF"
    password = "staff12345"
    venueId = "22222222-2222-2222-2222-222222222222"
}
Assert-Status $staffResponse 200 "OPS_MANAGER can create STAFF"
$staff = Read-Json $staffResponse
if ($staff.venueId -ne $venueId) {
    throw "OPS_MANAGER-created staff should receive own venueId. Expected $venueId but got $($staff.venueId)."
}

$adminCreateByOps = Post-Json $opsClient "$GatewayBaseUrl/api/users" @{
    email = "admin-$suffix@foundflow.local"
    role = "ADMIN"
    password = "admin12345"
    venueId = $null
}
Assert-Status $adminCreateByOps 403 "OPS_MANAGER cannot create ADMIN"

$opsSelfDelete = $opsClient.DeleteAsync("$GatewayBaseUrl/api/users/$($opsUser.id)").Result
Assert-Status $opsSelfDelete 403 "OPS_MANAGER cannot delete itself"

$foundResponse = Post-Json $opsClient "$GatewayBaseUrl/api/found-items" @{
    photoKey = "found-e2e"
    description = "E2E found item"
    foundAt = "2026-05-19T15:45:00"
    locationHint = "Desk"
    venueId = "33333333-3333-3333-3333-333333333333"
    reporterId = "44444444-4444-4444-4444-444444444444"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E")
    }
}
Assert-Status $foundResponse 201 "OPS_MANAGER can create found item in own venue"
$foundItem = Read-Json $foundResponse
if ($foundItem.venueId -ne $venueId) {
    throw "Found item should use OPS_MANAGER venueId. Expected $venueId but got $($foundItem.venueId)."
}

$lostResponse = Post-Json $publicClient "$GatewayBaseUrl/api/lost-items" @{
    photoKey = "lost-e2e"
    description = "E2E lost item"
    lostAt = "2026-05-19T15:50:00"
    location = "Desk"
    venueId = $venueId
    contactEmail = "lost-$suffix@example.com"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E")
    }
}
Assert-Status $lostResponse 201 "Public lost item can be created for test venue"
$lostItem = Read-Json $lostResponse

$matchResponse = Post-Json $opsClient "$GatewayBaseUrl/api/matches" @{
    foundItemId = $foundItem.id
    lostReportId = $lostItem.id
    venueId = $venueId
    attributeScore = 0.8
    semanticScore = 0.9
    combinedScore = 0.85
}
Assert-Status $matchResponse 201 "OPS_MANAGER can create same-venue match"

$otherLostResponse = Post-Json $publicClient "$GatewayBaseUrl/api/lost-items" @{
    photoKey = "lost-other-e2e"
    description = "E2E lost item other venue"
    lostAt = "2026-05-19T15:55:00"
    location = "Other"
    venueId = "99999999-9999-9999-9999-999999999999"
    contactEmail = "other-$suffix@example.com"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E")
    }
}
Assert-Status $otherLostResponse 201 "Public lost item can be created for another venue"
$otherLostItem = Read-Json $otherLostResponse

$crossVenueMatch = Post-Json $opsClient "$GatewayBaseUrl/api/matches" @{
    foundItemId = $foundItem.id
    lostReportId = $otherLostItem.id
    venueId = $venueId
    attributeScore = 0.8
    semanticScore = 0.9
    combinedScore = 0.85
}
Assert-Status $crossVenueMatch 403 "Cross-venue match is rejected"

$kpis = $adminClient.GetAsync("$GatewayBaseUrl/api/venues/kpis/$venueId").Result
Assert-Status $kpis 200 "Admin can read venue KPIs"
$kpiBody = Read-Json $kpis
if ($kpiBody.totalFoundItems -lt 1 -or $kpiBody.totalLostItems -lt 1 -or $kpiBody.totalMatches -lt 1 -or $kpiBody.pendingMatches -lt 1) {
    throw "Venue KPIs should include created found/lost/match data. Body: $($kpiBody | ConvertTo-Json -Depth 5)"
}

Logout-RefreshToken $opsTokens.refreshToken
Logout-RefreshToken $adminTokens.refreshToken

Write-Host "[OK] FoundFlow E2E suite completed successfully."
