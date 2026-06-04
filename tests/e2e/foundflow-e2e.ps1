param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@foundflow.local",
    [string]$AdminPassword = "admin12345"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

# The genai-service rejects sub-pixel placeholders (1x1 PNG) at the Pillow
# decode step before the provider is ever invoked, so for the extraction
# assertions (issue #128) we need bytes that pass validation. The golden
# umbrella image is already shipped with the repo and serves both the
# normal photo-flow assertions and the new GenAI ones.
$repoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$E2E_PHOTO_BYTES = [System.IO.File]::ReadAllBytes(
    (Join-Path $repoRoot "services/genai-service/tests/golden/images/umbrella-red.jpg")
)
$E2E_PHOTO_MEDIA_TYPE = "image/jpeg"
$E2E_PHOTO_FILE_NAME = "e2e-photo.jpg"

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

function Assert-RefreshRejected {
    param(
        [string]$RefreshToken,
        [string]$Label
    )

    $client = [System.Net.Http.HttpClient]::new()
    $response = $client.PostAsync("$GatewayBaseUrl/api/auth/refresh", (JsonContent @{
        refreshToken = $RefreshToken
    })).Result

    Assert-Status $response 401 $Label
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

function EmptyContent {
    return [System.Net.Http.StringContent]::new("")
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

# notification-service consumes match-invite / pickup-confirmation events
# asynchronously from RabbitMQ; the persist happens on the first attempt of
# the listener, *before* SMTP is touched, so the row shows up shortly after
# the staff-side action returns. Poll until one matching the URL marker
# arrives, then return its body so the caller can regex out the URL.
function Wait-ForNotification {
    param(
        [System.Net.Http.HttpClient]$Client,
        [string]$Email,
        [string]$UrlMarker,
        [int]$TimeoutSeconds = 15
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $response = $Client.GetAsync(
            "$GatewayBaseUrl/api/notifications?email=$([System.Uri]::EscapeDataString($Email))"
        ).Result
        if ($response.IsSuccessStatusCode) {
            $notifications = @(Read-Json $response)
            $matching = $notifications | Where-Object {
                $_.body -and $_.body.Contains($UrlMarker)
            } | Select-Object -First 1
            if ($matching) {
                return $matching
            }
        }
        Start-Sleep -Milliseconds 250
    }

    throw "Timed out waiting for notification to $Email whose body contains '$UrlMarker'."
}

function Extract-MagicLinkUrl {
    param(
        [string]$Body,
        [string]$UrlMarker
    )

    $regex = "https?://\S+$([regex]::Escape($UrlMarker))\S+"
    $match = [regex]::Match($Body, $regex)
    if (-not $match.Success) {
        throw "Could not extract magic-link URL with marker '$UrlMarker' from body: $Body"
    }
    return $match.Value
}

Write-Host "Running FoundFlow E2E tests against $GatewayBaseUrl"

$publicClient = New-GatewayClient

$health = $publicClient.GetAsync("$GatewayBaseUrl/actuator/health").Result
Assert-Status $health 200 "Gateway health is public"

$swaggerUi = $publicClient.GetAsync("$GatewayBaseUrl/swagger-ui/index.html").Result
Assert-Status $swaggerUi 200 "Gateway Swagger UI is public"

$gatewayApiDocs = $publicClient.GetAsync("$GatewayBaseUrl/v3/api-docs").Result
Assert-Status $gatewayApiDocs 200 "Gateway OpenAPI docs are public"

$authHealth = $publicClient.GetAsync("$GatewayBaseUrl/auth/actuator/health").Result
Assert-Status $authHealth 200 "Proxied auth health endpoint is public"

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
$usersByEmail = $adminClient.GetAsync("$GatewayBaseUrl/api/users/by-email?email=$([System.Uri]::EscapeDataString($AdminEmail))").Result
Assert-Status $usersByEmail 200 "Admin can load user by email"

$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
$venueResponse = Post-Json $adminClient "$GatewayBaseUrl/api/venues" @{
    name = "E2E Venue $suffix"
    tone = "friendly"
    defaultLanguage = "de"
}
Assert-Status $venueResponse 201 "Admin can create venue"
$venue = Read-Json $venueResponse
$venueId = $venue.id

$venueById = $adminClient.GetAsync("$GatewayBaseUrl/api/venues/$venueId").Result
Assert-Status $venueById 200 "Admin can load venue by id"

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

$staffTokens = Get-TokenPair $staffEmail "staff12345"
$staffClient = New-GatewayClient $staffTokens.accessToken

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

$foundCountResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/found-items/count?status=STORED").Result
Assert-Status $foundCountResponse 200 "OPS_MANAGER can read found-item count"
$foundCountBody = Read-Json $foundCountResponse
if ($foundCountBody.count -lt 1) {
    throw "Found-item count should include the created item."
}

$foundHistogramResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/found-items/histogram?status=STORED").Result
Assert-Status $foundHistogramResponse 200 "OPS_MANAGER can read found-item histogram"
$foundHistogramBody = Read-Json $foundHistogramResponse
if (@($foundHistogramBody.perDay).Count -lt 1) {
    throw "Found-item histogram should contain at least one daily bucket."
}

$lostCountResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/lost-items/count?status=OPEN").Result
Assert-Status $lostCountResponse 200 "OPS_MANAGER can read lost-item count"
$lostCountBody = Read-Json $lostCountResponse
if ($lostCountBody.count -lt 1) {
    throw "Lost-item count should include the created report."
}

$lostHistogramResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/lost-items/histogram?status=OPEN").Result
Assert-Status $lostHistogramResponse 200 "OPS_MANAGER can read lost-item histogram"
$lostHistogramBody = Read-Json $lostHistogramResponse
if (@($lostHistogramBody.perDay).Count -lt 1) {
    throw "Lost-item histogram should contain at least one daily bucket."
}

$matchResponse = Post-Json $opsClient "$GatewayBaseUrl/api/matches" @{
    foundItemId = $foundItem.id
    lostReportId = $lostItem.id
    venueId = $venueId
    attributeScore = 0.8
    semanticScore = 0.9
    combinedScore = 0.85
}
Assert-Status $matchResponse 201 "OPS_MANAGER can create same-venue match"
$match = Read-Json $matchResponse

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

$matchByIdResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/matches/$($match.id)").Result
Assert-Status $matchByIdResponse 200 "OPS_MANAGER can load match by id"

# Pickup slots are only generated from today onward (PickupService.weeklyDates clamps
# the start to LocalDate.now()), so anchor the schedule on a Monday computed at run time.
# Absolute dates rot: once "today" passes the hardcoded date the slot is no longer emitted.
$today = (Get-Date).Date
$daysUntilMonday = (1 - [int]$today.DayOfWeek + 7) % 7
if ($daysUntilMonday -eq 0) { $daysUntilMonday = 7 }  # always a strictly-future Monday
$pickupMonday = $today.AddDays($daysUntilMonday)
$pickupStartDate = $pickupMonday.ToString("yyyy-MM-dd")
$pickupEndDate = $pickupMonday.AddDays(7).ToString("yyyy-MM-dd")
$pickupSlot0900 = "$($pickupMonday.ToString('yyyy-MM-dd'))T09:00:00"
$pickupSlot0930 = "$($pickupMonday.ToString('yyyy-MM-dd'))T09:30:00"

$pickupScheduleResponse = Post-Json $opsClient "$GatewayBaseUrl/api/pickups/schedule" @{
    startDate = $pickupStartDate
    endDate = $pickupEndDate
    recurrenceType = "WEEKLY"
    dayOfWeek = "MONDAY"
    startTime = "09:00:00"
    endTime = "10:00:00"
    slotLengthInMinutes = 30
    venueId = "55555555-5555-5555-5555-555555555555"
}
Assert-Status $pickupScheduleResponse 201 "OPS_MANAGER can create pickup schedule"
$pickupSchedule = Read-Json $pickupScheduleResponse
if ($pickupSchedule.venueId -ne $venueId) {
    throw "Pickup schedule should use OPS_MANAGER venueId. Expected $venueId but got $($pickupSchedule.venueId)."
}

$publicMatchLinkResponse = Post-Json $opsClient "$GatewayBaseUrl/api/matches/$($match.id)/public-link" @{
    email = $lostItem.contactEmail
}
Assert-Status $publicMatchLinkResponse 200 "OPS_MANAGER can create public match link"
$publicMatchLink = Read-Json $publicMatchLinkResponse
if ([string]::IsNullOrWhiteSpace($publicMatchLink.token)) {
    throw "Public match link should contain a token."
}

# notification-service writes a row with the match magic link in body when it
# consumes the MatchInviteRequested event. Poll until it appears so we can
# verify the URL embedded in body matches the token returned by /public-link.
$matchInviteNotification = Wait-ForNotification `
    -Client $opsClient `
    -Email $lostItem.contactEmail `
    -UrlMarker "/api/matches/public/"
$matchInviteUrl = Extract-MagicLinkUrl -Body $matchInviteNotification.body -UrlMarker "/api/matches/public/"
if (-not $matchInviteUrl.EndsWith("/api/matches/public/$($publicMatchLink.token)")) {
    throw "Match-invite notification URL '$matchInviteUrl' should end with the public-link token '$($publicMatchLink.token)'."
}

$publicMatchResponse = $publicClient.GetAsync("$GatewayBaseUrl/api/matches/public/$($publicMatchLink.token)").Result
Assert-Status $publicMatchResponse 200 "Public match link can load match"
$publicMatch = Read-Json $publicMatchResponse
if ($publicMatch.id -ne $match.id) {
    throw "Public match response should return the linked match."
}

$publicConfirmResponse = $publicClient.PutAsync(
    "$GatewayBaseUrl/api/matches/public/match-links/$($publicMatchLink.token)/confirm",
    (EmptyContent)
).Result
Assert-Status $publicConfirmResponse 200 "Public match link can confirm match"

$publicSlotsResponse = $publicClient.GetAsync("$GatewayBaseUrl/api/pickups/public/$($publicMatchLink.token)").Result
Assert-Status $publicSlotsResponse 200 "Public pickup link can list pickup slots"
$publicSlots = @(Read-Json $publicSlotsResponse)
if (@($publicSlots | Where-Object { $_.startsAt -eq $pickupSlot0900 -and $_.available -eq $true }).Count -ne 1) {
    throw "Public pickup slots should contain the available 09:00 slot."
}

$publicPickupResponse = Post-Json $publicClient "$GatewayBaseUrl/api/pickups/public/$($publicMatchLink.token)" @{
    pickupAt = $pickupSlot0900
    email = $lostItem.contactEmail
}
Assert-Status $publicPickupResponse 201 "Public pickup link can schedule pickup"
$publicPickup = Read-Json $publicPickupResponse
if ($publicPickup.matchId -ne $match.id -or $publicPickup.venueId -ne $venueId) {
    throw "Public pickup should be linked to the confirmed match and venue."
}
if ([string]::IsNullOrWhiteSpace($publicPickup.manageUrl)) {
    throw "Public pickup response should include a manageUrl."
}

# Same poll-then-regex pattern for the pickup-confirmation notification. The
# extracted URL is the manage link the public client uses to update/cancel.
$pickupConfirmationNotification = Wait-ForNotification `
    -Client $opsClient `
    -Email $lostItem.contactEmail `
    -UrlMarker "/api/pickups/public/"
$manageLink = Extract-MagicLinkUrl `
    -Body $pickupConfirmationNotification.body `
    -UrlMarker "/api/pickups/public/"
$publicPickupUpdateResponse = $publicClient.PutAsync($manageLink, (JsonContent @{
    pickupAt = $pickupSlot0930
    email = $lostItem.contactEmail
})).Result
Assert-Status $publicPickupUpdateResponse 200 "Public pickup manage link can reschedule pickup"

$staffPickupListResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/pickups?venueId=$venueId").Result
Assert-Status $staffPickupListResponse 200 "OPS_MANAGER can list venue pickups"
$staffPickups = @(Read-Json $staffPickupListResponse)
if (@($staffPickups | Where-Object { $_.id -eq $publicPickup.id }).Count -ne 1) {
    throw "Staff pickup list should include the public pickup."
}

$publicPickupDeleteResponse = $publicClient.DeleteAsync($manageLink).Result
Assert-Status $publicPickupDeleteResponse 204 "Public pickup manage link can cancel pickup"

$matchListResponse = $opsClient.GetAsync(
    "$GatewayBaseUrl/api/matches?foundItem=$($foundItem.id)&lostItem=$($lostItem.id)&status=PENDING"
).Result
Assert-Status $matchListResponse 200 "OPS_MANAGER can list filtered matches"
$matchListBody = Read-Json $matchListResponse
if (@($matchListBody).Count -ne 0) {
    throw "Pending match list should be empty after public confirmation."
}

$matchCountResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/matches/count?status=CONFIRMED").Result
Assert-Status $matchCountResponse 200 "OPS_MANAGER can read match count"
$matchCountBody = Read-Json $matchCountResponse
if ($matchCountBody.count -lt 1) {
    throw "Match count should include the confirmed match."
}

$matchHistogramResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/matches/histogram?status=CONFIRMED").Result
Assert-Status $matchHistogramResponse 200 "OPS_MANAGER can read match histogram"
$matchHistogramBody = Read-Json $matchHistogramResponse
if (@($matchHistogramBody.perDay).Count -lt 1) {
    throw "Match histogram should contain at least one daily bucket."
}

$notificationCreateResponse = Post-Json $opsClient "$GatewayBaseUrl/api/notifications" @{
    matchId = $match.id
    venueId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    recipientAddress = "notify-$suffix@example.com"
    language = "de"
    subject = "Match gefunden"
    header = "Gute Nachrichten"
    body = "Bitte melden Sie sich."
}
Assert-Status $notificationCreateResponse 201 "OPS_MANAGER can create notification"
$notification = Read-Json $notificationCreateResponse
if ($notification.venueId -ne $venueId) {
    throw "Notification should use OPS_MANAGER venueId. Expected $venueId but got $($notification.venueId)."
}

$notificationListResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/notifications?email=$([System.Uri]::EscapeDataString($notification.recipientAddress))").Result
Assert-Status $notificationListResponse 200 "OPS_MANAGER can filter notifications by email"
$notificationListBody = Read-Json $notificationListResponse
if (@($notificationListBody).Count -ne 1) {
    throw "Notification email filter should return exactly one notification."
}

$notificationByIdResponse = $opsClient.GetAsync("$GatewayBaseUrl/api/notifications/$($notification.id)").Result
Assert-Status $notificationByIdResponse 200 "OPS_MANAGER can load notification by id"

$notificationUpdateResponse = $opsClient.PutAsync("$GatewayBaseUrl/api/notifications/$($notification.id)", (JsonContent @{
    matchId = $match.id
    venueId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    recipientAddress = $notification.recipientAddress
    language = "en"
    subject = "Updated subject"
    header = "Updated header"
    body = "Updated body"
    sentAt = "2026-05-19T16:15:00"
})).Result
Assert-Status $notificationUpdateResponse 200 "OPS_MANAGER can update notification"
$notificationUpdated = Read-Json $notificationUpdateResponse
if ($notificationUpdated.venueId -ne $venueId) {
    throw "OPS_MANAGER notification update should keep own venueId. Expected $venueId but got $($notificationUpdated.venueId)."
}
if ($notificationUpdated.language -ne "en" -or $notificationUpdated.sentAt -ne "2026-05-19T16:15:00") {
    throw "Notification update should persist changed language and sentAt."
}

$blueprintsRead = $opsClient.GetAsync("$GatewayBaseUrl/api/notifications/bluePrints").Result
Assert-Status $blueprintsRead 200 "OPS_MANAGER can list notification blueprints"

$staffBlueprintWrite = $staffClient.PostAsync("$GatewayBaseUrl/api/notifications/bluePrints", (EmptyContent)).Result
Assert-Status $staffBlueprintWrite 403 "STAFF cannot create notification blueprints"

$opsBlueprintWrite = $opsClient.PostAsync("$GatewayBaseUrl/api/notifications/bluePrints", (EmptyContent)).Result
Assert-Status $opsBlueprintWrite 202 "OPS_MANAGER can create notification blueprints"

$opsBlueprintUpdate = $opsClient.PutAsync(
    "$GatewayBaseUrl/api/notifications/bluePrints/00000000-0000-0000-0000-000000000001",
    (EmptyContent)
).Result
Assert-Status $opsBlueprintUpdate 202 "OPS_MANAGER can update notification blueprints"

$kpis = $adminClient.GetAsync("$GatewayBaseUrl/api/venues/kpis/$venueId").Result
Assert-Status $kpis 200 "Admin can read venue KPIs"
$kpiBody = Read-Json $kpis
if ($kpiBody.totalFoundItems -lt 1 -or $kpiBody.totalLostItems -lt 1 -or $kpiBody.totalMatches -lt 1) {
    throw "Venue KPIs should include created found/lost/match data. Body: $($kpiBody | ConvertTo-Json -Depth 5)"
}

# Issue #128 — GenAI attribute extraction wires through to persistence.
# CI sets GENAI_PROVIDER=fake; the fake provider returns a canned
# ItemAttributes JSON ({"category":"jacket",...}) so we can assert
# extraction actually ran end-to-end. Under a real provider
# (GENAI_PROVIDER=openai|local) the category value depends on the model,
# so we relax to a presence check — the contract is "extraction ran and
# populated a non-empty category", not the specific value.
$genaiProvider = if ($env:GENAI_PROVIDER) { $env:GENAI_PROVIDER } else { 'fake' }
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
if ($genaiProvider -eq 'fake') {
    if ($null -eq $extractionLostItem.attributes -or $extractionLostItem.attributes.category -ne "jacket") {
        throw "GenAI extraction did not populate canned 'jacket' on lost-item (fake provider). Body: $($extractionLostItem | ConvertTo-Json -Depth 5)"
    }
    Write-Host "[OK] Lost-item GenAI extraction populated category='jacket' (fake provider)"
} else {
    if ($null -eq $extractionLostItem.attributes -or [string]::IsNullOrWhiteSpace($extractionLostItem.attributes.category)) {
        throw "GenAI extraction did not populate a non-empty attributes.category on lost-item ($genaiProvider provider). Body: $($extractionLostItem | ConvertTo-Json -Depth 5)"
    }
    Write-Host "[OK] Lost-item GenAI extraction populated category='$($extractionLostItem.attributes.category)' ($genaiProvider provider)"
}

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
if ($genaiProvider -eq 'fake') {
    if ($null -eq $extractionFoundItem.attributes -or $extractionFoundItem.attributes.category -ne "jacket") {
        throw "GenAI extraction did not populate canned 'jacket' on found-item (fake provider). Body: $($extractionFoundItem | ConvertTo-Json -Depth 5)"
    }
    Write-Host "[OK] Found-item GenAI extraction populated category='jacket' (fake provider)"
} else {
    if ($null -eq $extractionFoundItem.attributes -or [string]::IsNullOrWhiteSpace($extractionFoundItem.attributes.category)) {
        throw "GenAI extraction did not populate a non-empty attributes.category on found-item ($genaiProvider provider). Body: $($extractionFoundItem | ConvertTo-Json -Depth 5)"
    }
    Write-Host "[OK] Found-item GenAI extraction populated category='$($extractionFoundItem.attributes.category)' ($genaiProvider provider)"
}

Logout-RefreshToken $opsTokens.refreshToken
Logout-RefreshToken $adminTokens.refreshToken
Logout-RefreshToken $staffTokens.refreshToken

Assert-RefreshRejected $opsTokens.refreshToken "Logged-out OPS_MANAGER refresh token is rejected"
Assert-RefreshRejected $adminTokens.refreshToken "Logged-out admin refresh token is rejected"
Assert-RefreshRejected $staffTokens.refreshToken "Logged-out STAFF refresh token is rejected"

Write-Host "[OK] FoundFlow E2E suite completed successfully."
