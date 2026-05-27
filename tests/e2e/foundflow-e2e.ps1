param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@foundflow.local",
    [string]$AdminPassword = "admin12345"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$E2E_PHOTO_BYTES = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/l2x2iQAAAABJRU5ErkJggg=="
)
$E2E_PHOTO_MEDIA_TYPE = "image/png"
$E2E_PHOTO_FILE_NAME = "e2e-photo.png"

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

function MultipartItemContent {
    param(
        [object]$RequestBody,
        [bool]$IncludePhoto = $true
    )

    $content = [System.Net.Http.MultipartFormDataContent]::new()
    $requestContent = [System.Net.Http.StringContent]::new(
        ($RequestBody | ConvertTo-Json -Depth 8),
        [Text.Encoding]::UTF8,
        "application/json"
    )
    [void]$content.Add($requestContent, "request")

    if ($IncludePhoto) {
        $photoContent = [System.Net.Http.ByteArrayContent]::new($E2E_PHOTO_BYTES)
        $photoContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($E2E_PHOTO_MEDIA_TYPE)
        [void]$content.Add($photoContent, "photo", $E2E_PHOTO_FILE_NAME)
    }

    return ,$content
}

function PhotoOnlyContent {
    $content = [System.Net.Http.MultipartFormDataContent]::new()
    $photoContent = [System.Net.Http.ByteArrayContent]::new($E2E_PHOTO_BYTES)
    $photoContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($E2E_PHOTO_MEDIA_TYPE)
    [void]$content.Add($photoContent, "photo", $E2E_PHOTO_FILE_NAME)
    return ,$content
}

function Assert-GeneratedPhotoKey {
    param(
        [object]$Item,
        [string]$ExpectedPrefix,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Item.photoKey) -or -not $Item.photoKey.StartsWith($ExpectedPrefix)) {
        throw "$Label should have generated photoKey with prefix '$ExpectedPrefix'. Actual: $($Item.photoKey)"
    }
}

function Assert-SignedPhotoUrl {
    param(
        [object]$Body,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Body.url)) {
        throw "$Label should return a non-empty signed photo URL."
    }
    if (-not [System.Uri]::IsWellFormedUriString($Body.url, [System.UriKind]::Absolute)) {
        throw "$Label should return an absolute signed photo URL. Actual: $($Body.url)"
    }
    if (-not $Body.url.Contains("X-Amz-Signature")) {
        throw "$Label should return a MinIO presigned URL. Actual: $($Body.url)"
    }
}

Write-Host "Running FoundFlow E2E tests against $GatewayBaseUrl"

$publicClient = New-GatewayClient

$health = $publicClient.GetAsync("$GatewayBaseUrl/actuator/health").Result
Assert-Status $health 200 "Gateway health is public"

$protectedWithoutToken = $publicClient.GetAsync("$GatewayBaseUrl/api/venues").Result
Assert-Status $protectedWithoutToken 401 "Protected endpoint rejects missing token"

$publicVenueId = "11111111-1111-1111-1111-111111111111"
$publicLostReportRequest = @{
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
$publicLostReport = $publicClient.PostAsync(
    "$GatewayBaseUrl/api/lost-items",
    (MultipartItemContent $publicLostReportRequest)
).Result
Assert-Status $publicLostReport 201 "Public lost-item report can be created without token"
$publicLostReportBody = Read-Json $publicLostReport
Assert-GeneratedPhotoKey $publicLostReportBody "lost-reports/" "Public lost-item multipart create"

$publicLostPhoto = $publicClient.GetAsync("$GatewayBaseUrl/api/lost-items/$($publicLostReportBody.id)/photo").Result
Assert-Status $publicLostPhoto 401 "Public client cannot read lost-item photo"
$publicLostPhotoUrl = $publicClient.GetAsync("$GatewayBaseUrl/api/lost-items/$($publicLostReportBody.id)/photo-url").Result
Assert-Status $publicLostPhotoUrl 401 "Public client cannot request lost-item signed photo URL"

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

$foundRequest = @{
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
$foundResponse = $opsClient.PostAsync(
    "$GatewayBaseUrl/api/found-items",
    (MultipartItemContent $foundRequest)
).Result
Assert-Status $foundResponse 201 "OPS_MANAGER can create found item in own venue"
$foundItem = Read-Json $foundResponse
if ($foundItem.venueId -ne $venueId) {
    throw "Found item should use OPS_MANAGER venueId. Expected $venueId but got $($foundItem.venueId)."
}
Assert-GeneratedPhotoKey $foundItem "found-items/" "Found-item multipart create"

$foundPhoto = $opsClient.GetAsync("$GatewayBaseUrl/api/found-items/$($foundItem.id)/photo").Result
Assert-Status $foundPhoto 200 "OPS_MANAGER can read found-item photo"
if ($foundPhoto.Content.Headers.ContentType.MediaType -ne $E2E_PHOTO_MEDIA_TYPE) {
    throw "Found-item photo should be returned as $E2E_PHOTO_MEDIA_TYPE but got $($foundPhoto.Content.Headers.ContentType)."
}

$foundPhotoUrl = $opsClient.GetAsync("$GatewayBaseUrl/api/found-items/$($foundItem.id)/photo-url").Result
Assert-Status $foundPhotoUrl 200 "OPS_MANAGER can request found-item signed photo URL"
Assert-SignedPhotoUrl (Read-Json $foundPhotoUrl) "Found-item signed photo URL"

$publicFoundPhoto = $publicClient.GetAsync("$GatewayBaseUrl/api/found-items/$($foundItem.id)/photo").Result
Assert-Status $publicFoundPhoto 401 "Public client cannot read found-item photo"
$publicFoundPhotoUrl = $publicClient.GetAsync("$GatewayBaseUrl/api/found-items/$($foundItem.id)/photo-url").Result
Assert-Status $publicFoundPhotoUrl 401 "Public client cannot request found-item signed photo URL"

$foundJsonUpdate = $opsClient.PutAsync("$GatewayBaseUrl/api/found-items/$($foundItem.id)", (JsonContent @{
    photoKey = "attacker-controlled-found-photo-key"
    description = "E2E found item JSON update"
    foundAt = "2026-05-19T15:46:00"
    locationHint = "Desk updated"
    status = "STORED"
    venueId = "33333333-3333-3333-3333-333333333333"
    reporterId = "44444444-4444-4444-4444-444444444444"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E", "JSON update")
    }
})).Result
Assert-Status $foundJsonUpdate 200 "Found item JSON update cannot replace photoKey"
$foundJsonUpdated = Read-Json $foundJsonUpdate
if ($foundJsonUpdated.photoKey -ne $foundItem.photoKey) {
    throw "Found item JSON update changed photoKey. Expected $($foundItem.photoKey) but got $($foundJsonUpdated.photoKey)."
}

$foundPhotoUpdate = $opsClient.PutAsync(
    "$GatewayBaseUrl/api/found-items/$($foundItem.id)/photo",
    (PhotoOnlyContent)
).Result
Assert-Status $foundPhotoUpdate 200 "OPS_MANAGER can replace found-item photo"
$foundPhotoUpdated = Read-Json $foundPhotoUpdate
Assert-GeneratedPhotoKey $foundPhotoUpdated "found-items/" "Found-item photo replacement"
if ($foundPhotoUpdated.photoKey -eq $foundItem.photoKey) {
    throw "Found item photo replacement should generate a new photoKey."
}
$foundItem = $foundPhotoUpdated

$lostRequest = @{
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
$lostResponse = $publicClient.PostAsync(
    "$GatewayBaseUrl/api/lost-items",
    (MultipartItemContent $lostRequest)
).Result
Assert-Status $lostResponse 201 "Public lost item can be created for test venue"
$lostItem = Read-Json $lostResponse
Assert-GeneratedPhotoKey $lostItem "lost-reports/" "Lost-item multipart create"

$lostPhoto = $opsClient.GetAsync("$GatewayBaseUrl/api/lost-items/$($lostItem.id)/photo").Result
Assert-Status $lostPhoto 200 "OPS_MANAGER can read lost-item photo"
if ($lostPhoto.Content.Headers.ContentType.MediaType -ne $E2E_PHOTO_MEDIA_TYPE) {
    throw "Lost-item photo should be returned as $E2E_PHOTO_MEDIA_TYPE but got $($lostPhoto.Content.Headers.ContentType)."
}

$lostPhotoUrl = $opsClient.GetAsync("$GatewayBaseUrl/api/lost-items/$($lostItem.id)/photo-url").Result
Assert-Status $lostPhotoUrl 200 "OPS_MANAGER can request lost-item signed photo URL"
Assert-SignedPhotoUrl (Read-Json $lostPhotoUrl) "Lost-item signed photo URL"

$lostJsonUpdate = $opsClient.PutAsync("$GatewayBaseUrl/api/lost-items/$($lostItem.id)", (JsonContent @{
    photoKey = "attacker-controlled-lost-photo-key"
    description = "E2E lost item JSON update"
    lostAt = "2026-05-19T15:51:00"
    location = "Desk updated"
    status = "OPEN"
    venueId = $venueId
    contactEmail = "lost-updated-$suffix@example.com"
    attributes = @{
        category = "Bag"
        brand = "Test"
        color = "Black"
        marks = @("E2E", "JSON update")
    }
})).Result
Assert-Status $lostJsonUpdate 200 "Lost item JSON update cannot replace photoKey"
$lostJsonUpdated = Read-Json $lostJsonUpdate
if ($lostJsonUpdated.photoKey -ne $lostItem.photoKey) {
    throw "Lost item JSON update changed photoKey. Expected $($lostItem.photoKey) but got $($lostJsonUpdated.photoKey)."
}

$lostPhotoUpdate = $opsClient.PutAsync(
    "$GatewayBaseUrl/api/lost-items/$($lostItem.id)/photo",
    (PhotoOnlyContent)
).Result
Assert-Status $lostPhotoUpdate 200 "OPS_MANAGER can replace lost-item photo"
$lostPhotoUpdated = Read-Json $lostPhotoUpdate
Assert-GeneratedPhotoKey $lostPhotoUpdated "lost-reports/" "Lost-item photo replacement"
if ($lostPhotoUpdated.photoKey -eq $lostItem.photoKey) {
    throw "Lost item photo replacement should generate a new photoKey."
}
$lostItem = $lostPhotoUpdated

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

# Issue #128 — GenAI attribute extraction wires through to persistence.
# CI sets GENAI_PROVIDER=fake; the fake provider returns a canned
# ItemAttributes JSON ({"category":"jacket",...}) so we can assert
# extraction actually ran end-to-end.
$extractionLostRequest = @{
    description = "E2E lost item without attributes"
    lostAt = "2026-05-19T16:00:00"
    location = "GenAI extraction test"
    venueId = $venueId
    contactEmail = "extract-$suffix@example.com"
}
$extractionLostResponse = $publicClient.PostAsync(
    "$GatewayBaseUrl/api/lost-items",
    (MultipartItemContent $extractionLostRequest)
).Result
Assert-Status $extractionLostResponse 201 "Lost-item create runs GenAI extraction"
$extractionLostItem = Read-Json $extractionLostResponse

# GenAI extraction happens after the response is sent in the current
# implementation? No - it's inline. The fake provider is synchronous and
# returns immediately, so the response body already carries attributes.
if ($null -eq $extractionLostItem.attributes -or $extractionLostItem.attributes.category -ne "jacket") {
    throw "GenAI extraction did not populate attributes on lost-item. Body: $($extractionLostItem | ConvertTo-Json -Depth 5)"
}
Write-Host "[OK] Lost-item GenAI extraction populated category='jacket'"

$extractionFoundRequest = @{
    description = "E2E found item without attributes"
    foundAt = "2026-05-19T16:05:00"
    locationHint = "GenAI extraction"
    venueId = "33333333-3333-3333-3333-333333333333"
    reporterId = "44444444-4444-4444-4444-444444444444"
}
$extractionFoundResponse = $opsClient.PostAsync(
    "$GatewayBaseUrl/api/found-items",
    (MultipartItemContent $extractionFoundRequest)
).Result
Assert-Status $extractionFoundResponse 201 "Found-item create runs GenAI extraction"
$extractionFoundItem = Read-Json $extractionFoundResponse
if ($null -eq $extractionFoundItem.attributes -or $extractionFoundItem.attributes.category -ne "jacket") {
    throw "GenAI extraction did not populate attributes on found-item. Body: $($extractionFoundItem | ConvertTo-Json -Depth 5)"
}
Write-Host "[OK] Found-item GenAI extraction populated category='jacket'"

Logout-RefreshToken $opsTokens.refreshToken
Logout-RefreshToken $adminTokens.refreshToken

Write-Host "[OK] FoundFlow E2E suite completed successfully."
