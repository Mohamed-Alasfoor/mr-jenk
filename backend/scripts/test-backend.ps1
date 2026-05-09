param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$DiscoveryBaseUrl = "http://localhost:8761",
    [string]$UserServiceBaseUrl = "http://localhost:8081",
    [string]$ProductServiceBaseUrl = "http://localhost:8082",
    [string]$MediaServiceBaseUrl = "http://localhost:8083",
    [switch]$UseHttps,
    [switch]$SkipCertificateValidation,
    [string]$AdminEmail,
    [string]$AdminPassword,
    [int]$AuthRateLimitMaxRequests = 30,
    [int]$MediaWriteRateLimitMaxRequests = 60,
    [switch]$SkipRateLimitTests,
    [switch]$KeepArtifacts
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-ToHttpsUrl {
    param([string]$Url)

    if ($Url -match "^http://") {
        return "https://" + $Url.Substring(7)
    }

    return $Url
}

if ($UseHttps) {
    $GatewayBaseUrl = Convert-ToHttpsUrl -Url $GatewayBaseUrl
    $DiscoveryBaseUrl = Convert-ToHttpsUrl -Url $DiscoveryBaseUrl
    $UserServiceBaseUrl = Convert-ToHttpsUrl -Url $UserServiceBaseUrl
    $ProductServiceBaseUrl = Convert-ToHttpsUrl -Url $ProductServiceBaseUrl
    $MediaServiceBaseUrl = Convert-ToHttpsUrl -Url $MediaServiceBaseUrl
}

try {
    Add-Type -AssemblyName System.Net.Http
} catch {
    throw "Failed to load System.Net.Http. Run this script with Windows PowerShell 5.1+ or PowerShell 7+."
}

$hasAdminEmail = -not [string]::IsNullOrWhiteSpace($AdminEmail)
$hasAdminPassword = -not [string]::IsNullOrWhiteSpace($AdminPassword)
if ($hasAdminEmail -xor $hasAdminPassword) {
    throw "Provide both -AdminEmail and -AdminPassword or neither."
}

$script:RunAdminBootstrapTests = $hasAdminEmail -and $hasAdminPassword
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:PassCount = 0
$script:RunId = Get-Date -Format "yyyyMMddHHmmss"
$script:TempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("buy01-backend-test-" + $script:RunId)
$script:HttpClientHandler = New-Object System.Net.Http.HttpClientHandler
if ($SkipCertificateValidation) {
    $script:HttpClientHandler.ServerCertificateCustomValidationCallback = { return $true }
}
$script:HttpClient = New-Object System.Net.Http.HttpClient($script:HttpClientHandler)
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds(30)

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Add-Pass {
    param([string]$Name)
    $script:PassCount++
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Add-Fail {
    param(
        [string]$Name,
        [string]$Detail
    )
    $message = "$Name :: $Detail"
    $script:Failures.Add($message)
    Write-Host "[FAIL] $message" -ForegroundColor Red
}

function Require {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Get-HttpMethod {
    param([string]$Method)

    switch ($Method.ToUpperInvariant()) {
        "GET" { return [System.Net.Http.HttpMethod]::Get }
        "POST" { return [System.Net.Http.HttpMethod]::Post }
        "PUT" { return [System.Net.Http.HttpMethod]::Put }
        "DELETE" { return [System.Net.Http.HttpMethod]::Delete }
        "PATCH" { return [System.Net.Http.HttpMethod]::Patch }
        default { return [System.Net.Http.HttpMethod]::new($Method.ToUpperInvariant()) }
    }
}

function Convert-ToParsedJson {
    param([string]$BodyText)

    if ([string]::IsNullOrWhiteSpace($BodyText)) {
        return $null
    }

    try {
        return $BodyText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        return $null
    }
}

function Get-ObjectPropertyValue {
    param(
        $Object,
        [string]$PropertyName
    )

    if ($null -eq $Object) {
        return $null
    }

    if ($Object -is [System.Collections.IDictionary]) {
        if ($Object.Contains($PropertyName)) {
            return $Object[$PropertyName]
        }
        return $null
    }

    $property = $Object.PSObject.Properties[$PropertyName]
    if ($null -ne $property) {
        return $property.Value
    }

    return $null
}

function New-ResponseObject {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [byte[]]$Bytes
    )

    $contentType = $null
    if ($null -ne $Response.Content.Headers.ContentType) {
        $contentType = $Response.Content.Headers.ContentType.MediaType
    }

    $bodyText = ""
    if ($Bytes.Length -gt 0) {
        try {
            $bodyText = [System.Text.Encoding]::UTF8.GetString($Bytes)
        } catch {
            $bodyText = ""
        }
    }

    return [pscustomobject]@{
        StatusCode     = [int]$Response.StatusCode
        Body           = $bodyText
        Json           = Convert-ToParsedJson -BodyText $bodyText
        Bytes          = $Bytes
        ContentType    = $contentType
        Headers        = $Response.Headers
        ContentHeaders = $Response.Content.Headers
    }
}

function Add-RequestHeaders {
    param(
        [System.Net.Http.HttpRequestMessage]$Request,
        [hashtable]$Headers
    )

    if ($null -eq $Headers) {
        return
    }

    foreach ($key in $Headers.Keys) {
        [void]$Request.Headers.TryAddWithoutValidation([string]$key, [string]$Headers[$key])
    }
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body,
        [string]$Token,
        [hashtable]$Headers
    )

    $request = [System.Net.Http.HttpRequestMessage]::new((Get-HttpMethod -Method $Method), $Url)
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }
    Add-RequestHeaders -Request $request -Headers $Headers

    if ($PSBoundParameters.ContainsKey("Body") -and $null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
        $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
    }

    try {
        $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
        try {
            $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            return New-ResponseObject -Response $response -Bytes $bytes
        } finally {
            $response.Dispose()
        }
    } finally {
        $request.Dispose()
    }
}

function Invoke-MultipartRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string]$FilePath,
        [string]$FileContentType,
        [string]$Token,
        [hashtable]$Fields,
        [hashtable]$Headers,
        [string]$FileFieldName = "file"
    )

    $request = [System.Net.Http.HttpRequestMessage]::new((Get-HttpMethod -Method $Method), $Url)
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }
    Add-RequestHeaders -Request $request -Headers $Headers

    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = $null

    try {
        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $fileContent = [System.Net.Http.ByteArrayContent]::new($fileBytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($FileContentType)
        $multipart.Add($fileContent, $FileFieldName, [System.IO.Path]::GetFileName($FilePath))

        if ($null -ne $Fields) {
            foreach ($key in $Fields.Keys) {
                $value = [string]$Fields[$key]
                $multipart.Add([System.Net.Http.StringContent]::new($value), $key)
            }
        }

        $request.Content = $multipart
        $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
        try {
            $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            return New-ResponseObject -Response $response -Bytes $bytes
        } finally {
            $response.Dispose()
        }
    } finally {
        if ($null -ne $fileContent) {
            $fileContent.Dispose()
        }
        $multipart.Dispose()
        $request.Dispose()
    }
}

function Invoke-MultipartUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$FileContentType,
        [string]$Token,
        [hashtable]$Fields,
        [hashtable]$Headers,
        [string]$FileFieldName = "file"
    )

    return Invoke-MultipartRequest `
        -Method "POST" `
        -Url $Url `
        -FilePath $FilePath `
        -FileContentType $FileContentType `
        -Token $Token `
        -Fields $Fields `
        -Headers $Headers `
        -FileFieldName $FileFieldName
}

function Invoke-CurlMultipartUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$FileContentType,
        [string]$Token,
        [hashtable]$Fields,
        [hashtable]$Headers,
        [string]$FileFieldName = "file"
    )

    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($null -eq $curl) {
        throw "curl.exe is required for oversized multipart verification on Windows PowerShell"
    }

    $args = @(
        "-s",
        "-o", "-",
        "-w", "`nHTTPSTATUS:%{http_code}`n"
    )

    if ($SkipCertificateValidation -and $Url -match "^https://") {
        $args += "-k"
    }

    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $args += @("-H", "Authorization: Bearer $Token")
    }

    if ($null -ne $Headers) {
        foreach ($key in $Headers.Keys) {
            $args += @("-H", ("{0}: {1}" -f [string]$key, [string]$Headers[$key]))
        }
    }

    if ($null -ne $Fields) {
        foreach ($key in $Fields.Keys) {
            $args += @("-F", "$key=$([string]$Fields[$key])")
        }
    }

    $args += @("-F", "$FileFieldName=@$FilePath;type=$FileContentType")
    $args += $Url

    $output = & curl.exe @args
    $joined = ($output -join [Environment]::NewLine)
    $marker = "HTTPSTATUS:"
    $markerIndex = $joined.LastIndexOf($marker)

    if ($markerIndex -lt 0) {
        throw "Could not parse curl response status. Raw output: $joined"
    }

    $body = $joined.Substring(0, $markerIndex).Trim()
    $statusText = $joined.Substring($markerIndex + $marker.Length).Trim()
    $statusCode = 0
    [void][int]::TryParse($statusText, [ref]$statusCode)

    return [pscustomobject]@{
        StatusCode     = $statusCode
        Body           = $body
        Json           = Convert-ToParsedJson -BodyText $body
        Bytes          = [System.Text.Encoding]::UTF8.GetBytes($body)
        ContentType    = "application/json"
        Headers        = $null
        ContentHeaders = $null
    }
}

function Get-HeaderValue {
    param(
        $Response,
        [string]$HeaderName
    )

    if ($null -ne $Response.Headers) {
        $values = $null
        if ($Response.Headers.TryGetValues($HeaderName, [ref]$values)) {
            return ($values -join ", ")
        }
    }

    if ($null -ne $Response.ContentHeaders) {
        $values = $null
        if ($Response.ContentHeaders.TryGetValues($HeaderName, [ref]$values)) {
            return ($values -join ", ")
        }
    }

    return $null
}

function Get-MediaIdFromImageUrl {
    param([string]$ImageUrl)

    if ([string]::IsNullOrWhiteSpace($ImageUrl)) {
        return $null
    }

    $segments = $ImageUrl.TrimEnd("/") -split "/"
    return $segments[-1]
}

function Get-ResponseSummary {
    param($Response)

    $body = $Response.Body
    if ($body.Length -gt 300) {
        $body = $body.Substring(0, 300) + "..."
    }

    return "status=$($Response.StatusCode), contentType=$($Response.ContentType), body=$body"
}

function Require-Status {
    param(
        $Response,
        [int]$ExpectedStatus
    )

    if ($Response.StatusCode -ne $ExpectedStatus) {
        throw "Expected status $ExpectedStatus but got $(Get-ResponseSummary -Response $Response)"
    }
}

function Wait-ForExpectedStatus {
    param(
        [string]$Name,
        [scriptblock]$Request,
        [int[]]$ExpectedStatuses,
        [int]$TimeoutSeconds = 45,
        [int]$DelayMilliseconds = 1500
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastResponse = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $lastResponse = & $Request
            if ($ExpectedStatuses -contains $lastResponse.StatusCode) {
                return $lastResponse
            }
        } catch {
            $lastResponse = $null
        }

        Start-Sleep -Milliseconds $DelayMilliseconds
    }

    if ($null -ne $lastResponse) {
        throw "$Name did not become ready within $TimeoutSeconds seconds. Last response: $(Get-ResponseSummary -Response $lastResponse)"
    }

    throw "$Name did not become ready within $TimeoutSeconds seconds."
}

function Run-Test {
    param(
        [string]$Name,
        [scriptblock]$ScriptBlock
    )

    try {
        & $ScriptBlock
        Add-Pass -Name $Name
    } catch {
        Add-Fail -Name $Name -Detail $_.Exception.Message
    }
}

function New-TestUserPayload {
    param(
        [string]$Role,
        [string]$Prefix
    )

    $email = "{0}-{1}@example.com" -f $Prefix, $script:RunId
    return [pscustomobject]@{
        email    = $email
        password = "Password123"
        fullName = ($Prefix + " User")
        role     = $Role
    }
}

function Initialize-TestFiles {
    New-Item -ItemType Directory -Path $script:TempDirectory -Force | Out-Null

    $smallPngPath = Join-Path $script:TempDirectory "valid-image.png"
    $textFilePath = Join-Path $script:TempDirectory "not-image.txt"
    $largePngPath = Join-Path $script:TempDirectory "large-image.png"

    $smallPngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0l8AAAAASUVORK5CYII="
    [System.IO.File]::WriteAllBytes($smallPngPath, [Convert]::FromBase64String($smallPngBase64))
    [System.IO.File]::WriteAllText($textFilePath, "plain text file for invalid media upload")

    $largeBytes = New-Object byte[] 2097153
    $largeBytes[0] = 0x89
    $largeBytes[1] = 0x50
    $largeBytes[2] = 0x4E
    $largeBytes[3] = 0x47
    $largeBytes[4] = 0x0D
    $largeBytes[5] = 0x0A
    $largeBytes[6] = 0x1A
    $largeBytes[7] = 0x0A
    [System.IO.File]::WriteAllBytes($largePngPath, $largeBytes)

    return [pscustomobject]@{
        SmallPng = $smallPngPath
        TextFile = $textFilePath
        LargePng = $largePngPath
    }
}

Write-Info "Preparing temp files in $script:TempDirectory"
if ($SkipCertificateValidation) {
    Write-Info "TLS certificate validation is disabled for this run"
}
if ($script:RunAdminBootstrapTests) {
    Write-Info "Admin bootstrap verification is enabled for $AdminEmail"
} else {
    Write-Info "Admin bootstrap verification is skipped because admin credentials were not provided"
}
if ($SkipRateLimitTests) {
    Write-Info "Gateway rate-limit verification is skipped"
}

$files = Initialize-TestFiles

$sellerOne = New-TestUserPayload -Role "SELLER" -Prefix "seller-one"
$sellerTwo = New-TestUserPayload -Role "SELLER" -Prefix "seller-two"
$clientUser = New-TestUserPayload -Role "CLIENT" -Prefix "client-one"
$blockedAdminUser = New-TestUserPayload -Role "ADMIN" -Prefix "admin-blocked"

$sellerOneToken = $null
$sellerTwoToken = $null
$clientToken = $null
$adminToken = $null
$productId = $null
$mediaId = $null
$imageUrl = $null
$sellerTwoMediaId = $null
$sellerTwoImageUrl = $null
$firstAvatarUrl = $null
$currentAvatarUrl = $null

Run-Test "Discovery health endpoint" {
    $response = Wait-ForExpectedStatus -Name "Discovery health endpoint" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$DiscoveryBaseUrl/actuator/health"
    }
    if ($null -ne $response.Json) {
        Require ((Get-ObjectPropertyValue -Object $response.Json -PropertyName "status") -eq "UP") "Expected discovery health status to be UP"
    }
}

Run-Test "Gateway health endpoint" {
    [void](Wait-ForExpectedStatus -Name "Gateway health endpoint" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/actuator/health"
    })
}

Run-Test "User service health endpoint" {
    [void](Wait-ForExpectedStatus -Name "User service health endpoint" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$UserServiceBaseUrl/actuator/health"
    })
}

Run-Test "Product service health endpoint" {
    [void](Wait-ForExpectedStatus -Name "Product service health endpoint" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$ProductServiceBaseUrl/actuator/health"
    })
}

Run-Test "Media service health endpoint" {
    [void](Wait-ForExpectedStatus -Name "Media service health endpoint" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$MediaServiceBaseUrl/actuator/health"
    })
}

Run-Test "Gateway routing is ready for registered services" {
    [void](Wait-ForExpectedStatus -Name "Gateway route to user-service" -ExpectedStatuses @(405) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/auth/register"
    })
    [void](Wait-ForExpectedStatus -Name "Gateway route to product-service" -ExpectedStatuses @(200) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products"
    })
    [void](Wait-ForExpectedStatus -Name "Gateway route to media-service" -ExpectedStatuses @(404) -TimeoutSeconds 90 -Request {
        Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images/route-readiness-probe"
    })
}

Run-Test "Unknown gateway route returns 404" {
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/does-not-exist"
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Wrong method on valid route returns 405" {
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/auth/register"
    Require-Status -Response $response -ExpectedStatus 405
}

Run-Test "Register seller one" {
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/register" -Body $sellerOne
    Require-Status -Response $response -ExpectedStatus 201
    $token = Get-ObjectPropertyValue -Object $response.Json -PropertyName "token"
    Require ($null -ne $token) "Expected JWT token in register response. Raw body: $($response.Body)"
    $script:sellerOneToken = [string]$token
}

Run-Test "Register duplicate seller one email returns 400" {
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/register" -Body $sellerOne
    Require-Status -Response $response -ExpectedStatus 400
}

Run-Test "Register seller two" {
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/register" -Body $sellerTwo
    Require-Status -Response $response -ExpectedStatus 201
    $token = Get-ObjectPropertyValue -Object $response.Json -PropertyName "token"
    Require ($null -ne $token) "Expected JWT token in register response. Raw body: $($response.Body)"
    $script:sellerTwoToken = [string]$token
}

Run-Test "Register client user" {
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/register" -Body $clientUser
    Require-Status -Response $response -ExpectedStatus 201
    $token = Get-ObjectPropertyValue -Object $response.Json -PropertyName "token"
    Require ($null -ne $token) "Expected JWT token in register response. Raw body: $($response.Body)"
    $script:clientToken = [string]$token
}

Run-Test "Public registration cannot assign admin role" {
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/register" -Body $blockedAdminUser
    Require-Status -Response $response -ExpectedStatus 400
}

if (-not $SkipRateLimitTests) {
    Run-Test "Gateway auth rate limiting returns 429 after configured threshold" {
        $headers = @{ "X-Forwarded-For" = "198.51.100.11" }
        $body = @{
            email    = "missing-$($script:RunId)@example.com"
            password = "WrongPassword123"
        }

        for ($attempt = 1; $attempt -le $AuthRateLimitMaxRequests; $attempt++) {
            $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/login" -Body $body -Headers $headers
            Require-Status -Response $response -ExpectedStatus 401
        }

        $limitedResponse = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/login" -Body $body -Headers $headers
        Require-Status -Response $limitedResponse -ExpectedStatus 429
        $message = Get-ObjectPropertyValue -Object $limitedResponse.Json -PropertyName "message"
        Require ($message -like "*Too many requests for auth*") "Expected auth rate-limit message. Raw body: $($limitedResponse.Body)"
    }
}

Run-Test "Login seller one" {
    $body = @{
        email    = $sellerOne.email
        password = $sellerOne.password
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/login" -Body $body
    Require-Status -Response $response -ExpectedStatus 200
    $token = Get-ObjectPropertyValue -Object $response.Json -PropertyName "token"
    Require ($null -ne $token) "Expected JWT token in login response. Raw body: $($response.Body)"
    $script:sellerOneToken = [string]$token
}

Run-Test "Login with wrong password returns 401" {
    $body = @{
        email    = $sellerOne.email
        password = "WrongPassword123"
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/login" -Body $body
    Require-Status -Response $response -ExpectedStatus 401
}

Run-Test "Get current profile without token returns 401" {
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/me"
    Require-Status -Response $response -ExpectedStatus 401
}

Run-Test "Get current profile with seller token" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/me" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $email = Get-ObjectPropertyValue -Object $response.Json -PropertyName "email"
    Require ($email -eq $sellerOne.email) "Expected seller one email in /me response. Raw body: $($response.Body)"
}

Run-Test "Update current profile with JSON payload" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $body = @{
        fullName = "seller-one updated"
    }
    $response = Invoke-JsonRequest -Method "PUT" -Url "$GatewayBaseUrl/me" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $fullName = Get-ObjectPropertyValue -Object $response.Json -PropertyName "fullName"
    Require ($fullName -eq "seller-one updated") "Expected updated fullName in /me response. Raw body: $($response.Body)"
}

Run-Test "Multipart avatar update rejects non-image file" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $response = Invoke-MultipartRequest -Method "PUT" -Url "$GatewayBaseUrl/me" -FilePath $files.TextFile -FileContentType "text/plain" -Token $sellerOneToken -Fields @{ fullName = "seller-one invalid avatar" } -FileFieldName "avatar"
    Require-Status -Response $response -ExpectedStatus 400
}

Run-Test "Seller uploads first avatar through profile endpoint" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $response = Invoke-MultipartRequest -Method "PUT" -Url "$GatewayBaseUrl/me" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerOneToken -Fields @{ fullName = "seller-one avatar first" } -FileFieldName "avatar"
    Require-Status -Response $response -ExpectedStatus 200
    $avatarUrl = Get-ObjectPropertyValue -Object $response.Json -PropertyName "avatarUrl"
    Require (-not [string]::IsNullOrWhiteSpace([string]$avatarUrl)) "Expected avatarUrl in multipart /me response. Raw body: $($response.Body)"
    $script:firstAvatarUrl = [string]$avatarUrl
    $script:currentAvatarUrl = [string]$avatarUrl
}

Run-Test "Seller replaces avatar and old avatar is removed" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($firstAvatarUrl)) "First avatar URL is missing"
    $response = Invoke-MultipartRequest -Method "PUT" -Url "$GatewayBaseUrl/me" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerOneToken -Fields @{ fullName = "seller-one avatar second" } -FileFieldName "avatar"
    Require-Status -Response $response -ExpectedStatus 200

    $updatedAvatarUrl = [string](Get-ObjectPropertyValue -Object $response.Json -PropertyName "avatarUrl")
    Require (-not [string]::IsNullOrWhiteSpace($updatedAvatarUrl)) "Expected new avatar URL in /me response. Raw body: $($response.Body)"
    Require ($updatedAvatarUrl -ne $firstAvatarUrl) "Expected avatar replacement to create a new image URL"
    $script:currentAvatarUrl = $updatedAvatarUrl

    $oldAvatarResponse = Invoke-JsonRequest -Method "GET" -Url $firstAvatarUrl
    Require-Status -Response $oldAvatarResponse -ExpectedStatus 404
}

Run-Test "Current avatar is publicly downloadable" {
    Require (-not [string]::IsNullOrWhiteSpace($currentAvatarUrl)) "Current avatar URL is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url $currentAvatarUrl
    Require-Status -Response $response -ExpectedStatus 200
    Require ($response.ContentType -eq "image/png") "Expected avatar contentType image/png but got $($response.ContentType)"
    Require ($response.Bytes.Length -gt 0) "Expected non-empty avatar body"
}

Run-Test "Public product list works without token" {
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products"
    Require-Status -Response $response -ExpectedStatus 200
}

Run-Test "Anonymous product creation returns 401" {
    $body = @{
        name        = "Anonymous Product"
        description = "Should not be created"
        price       = 100.00
        quantity    = 1
        imageUrls   = @()
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/products" -Body $body
    Require-Status -Response $response -ExpectedStatus 401
}

Run-Test "Client product creation returns 403" {
    Require (-not [string]::IsNullOrWhiteSpace($clientToken)) "Client token is missing"
    $body = @{
        name        = "Client Product"
        description = "Clients cannot create products"
        price       = 100.00
        quantity    = 1
        imageUrls   = @()
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/products" -Body $body -Token $clientToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Seller product validation returns 400 for invalid payload" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $body = @{
        name        = ""
        description = "Invalid product"
        price       = 0
        quantity    = -1
        imageUrls   = @()
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/products" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 400
}

Run-Test "Seller creates product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $body = @{
        name        = "Phone $($script:RunId)"
        description = "Flagship phone"
        price       = 699.99
        quantity    = 5
        imageUrls   = @()
    }
    $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/products" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 201
    $id = Get-ObjectPropertyValue -Object $response.Json -PropertyName "id"
    Require ($null -ne $id) "Expected product id in create response. Raw body: $($response.Body)"
    $script:productId = [string]$id
}

Run-Test "Owner product listing returns created product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products/me" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $ids = @($response.Json | ForEach-Object { $_.id })
    Require ($ids -contains $productId) "Expected created product in /products/me"
}

Run-Test "Public get created product by id" {
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products/$productId"
    Require-Status -Response $response -ExpectedStatus 200
    $id = Get-ObjectPropertyValue -Object $response.Json -PropertyName "id"
    Require ($id -eq $productId) "Expected matching product id. Raw body: $($response.Body)"
}

Run-Test "Non-owner seller cannot update product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $body = @{
        name        = "Illegal Update"
        description = "Should fail"
        price       = 1.00
        quantity    = 1
        imageUrls   = @()
    }
    $response = Invoke-JsonRequest -Method "PUT" -Url "$GatewayBaseUrl/products/$productId" -Body $body -Token $sellerTwoToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Upload with nonexistent product id returns 404" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $fields = @{ productId = "missing-product-$($script:RunId)" }
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerOneToken -Fields $fields
    Require-Status -Response $response -ExpectedStatus 404
}

Run-Test "Non-owner seller cannot upload image for another seller product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $fields = @{ productId = $productId }
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerTwoToken -Fields $fields
    Require-Status -Response $response -ExpectedStatus 404
}

Run-Test "Invalid text media upload returns 415" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.TextFile -FileContentType "text/plain" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 415
}

Run-Test "Oversized image upload returns 400" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    $response = Invoke-CurlMultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.LargePng -FileContentType "image/png" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 400
}

Run-Test "Anonymous media list returns 401" {
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images"
    Require-Status -Response $response -ExpectedStatus 401
}

Run-Test "Client media upload returns 403" {
    Require (-not [string]::IsNullOrWhiteSpace($clientToken)) "Client token is missing"
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $clientToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Client media list returns 403" {
    Require (-not [string]::IsNullOrWhiteSpace($clientToken)) "Client token is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images" -Token $clientToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Seller uploads valid image linked to own product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $fields = @{ productId = $productId }
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerOneToken -Fields $fields
    Require-Status -Response $response -ExpectedStatus 201
    $id = Get-ObjectPropertyValue -Object $response.Json -PropertyName "id"
    $returnedImageUrl = Get-ObjectPropertyValue -Object $response.Json -PropertyName "imageUrl"
    Require ($null -ne $id) "Expected media id in upload response. Raw body: $($response.Body)"
    Require ($null -ne $returnedImageUrl) "Expected imageUrl in upload response. Raw body: $($response.Body)"
    $script:mediaId = [string]$id
    $script:imageUrl = [string]$returnedImageUrl
}

Run-Test "Seller media list filtered by product contains uploaded image" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images?productId=$productId" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $ids = @($response.Json | ForEach-Object { $_.id })
    Require ($ids -contains $mediaId) "Expected product-linked media in filtered /media/images response"
}

Run-Test "Seller two uploads image without product association" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    $response = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerTwoToken
    Require-Status -Response $response -ExpectedStatus 201
    $id = Get-ObjectPropertyValue -Object $response.Json -PropertyName "id"
    $returnedImageUrl = Get-ObjectPropertyValue -Object $response.Json -PropertyName "imageUrl"
    Require ($null -ne $id) "Expected media id for seller two upload. Raw body: $($response.Body)"
    Require ($null -ne $returnedImageUrl) "Expected imageUrl for seller two upload. Raw body: $($response.Body)"
    $script:sellerTwoMediaId = [string]$id
    $script:sellerTwoImageUrl = [string]$returnedImageUrl
}

Run-Test "Seller media list contains uploaded image" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $ids = @($response.Json | ForEach-Object { $_.id })
    Require ($ids -contains $mediaId) "Expected uploaded media in /media/images"
}

Run-Test "Public download uploaded image returns caching headers" {
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images/$mediaId"
    Require-Status -Response $response -ExpectedStatus 200
    Require ($response.ContentType -eq "image/png") "Expected contentType image/png but got $($response.ContentType)"
    Require ($response.Bytes.Length -gt 0) "Expected non-empty image body"

    $cacheControl = Get-HeaderValue -Response $response -HeaderName "Cache-Control"
    $etag = Get-HeaderValue -Response $response -HeaderName "ETag"
    $lastModified = Get-HeaderValue -Response $response -HeaderName "Last-Modified"
    $contentDisposition = Get-HeaderValue -Response $response -HeaderName "Content-Disposition"

    Require (-not [string]::IsNullOrWhiteSpace($cacheControl)) "Expected Cache-Control header on image download response"
    Require ($cacheControl -like "*max-age*") "Expected Cache-Control max-age on image download response but got '$cacheControl'"
    Require (-not [string]::IsNullOrWhiteSpace($etag)) "Expected ETag header on image download response"
    Require (-not [string]::IsNullOrWhiteSpace($lastModified)) "Expected Last-Modified header on image download response"
    Require ($contentDisposition -like "*inline*") "Expected inline Content-Disposition header but got '$contentDisposition'"
}

Run-Test "Owner updates product with uploaded image URL" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    Require (-not [string]::IsNullOrWhiteSpace($imageUrl)) "Image URL is missing"
    $body = @{
        name        = "Phone Updated $($script:RunId)"
        description = "Flagship phone updated"
        price       = 749.99
        quantity    = 7
        imageUrls   = @($imageUrl)
    }
    $response = Invoke-JsonRequest -Method "PUT" -Url "$GatewayBaseUrl/products/$productId" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 200
    $imageUrls = @(Get-ObjectPropertyValue -Object $response.Json -PropertyName "imageUrls")
    Require ($imageUrls -contains $imageUrl) "Expected updated product to contain uploaded image URL"
}

Run-Test "Owner cannot attach unmanaged external image URL" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $body = @{
        name        = "Phone Invalid External Image $($script:RunId)"
        description = "Should fail due to unmanaged URL"
        price       = 749.99
        quantity    = 7
        imageUrls   = @("https://example.com/not-managed.png")
    }
    $response = Invoke-JsonRequest -Method "PUT" -Url "$GatewayBaseUrl/products/$productId" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 400
}

Run-Test "Owner cannot attach another seller image URL" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoImageUrl)) "Seller two image URL is missing"
    $body = @{
        name        = "Phone Invalid Image $($script:RunId)"
        description = "Should fail due to ownership"
        price       = 749.99
        quantity    = 7
        imageUrls   = @($sellerTwoImageUrl)
    }
    $response = Invoke-JsonRequest -Method "PUT" -Url "$GatewayBaseUrl/products/$productId" -Body $body -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Non-owner seller cannot delete media" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$mediaId" -Token $sellerTwoToken
    Require-Status -Response $response -ExpectedStatus 403
}

Run-Test "Non-owner seller cannot delete product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/products/$productId" -Token $sellerTwoToken
    Require-Status -Response $response -ExpectedStatus 403
}

if (-not $SkipRateLimitTests) {
    Run-Test "Gateway media write rate limiting returns 429 after configured threshold" {
        Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
        $headers = @{ "X-Forwarded-For" = "198.51.100.22" }
        $fakeMediaId = "missing-media-$($script:RunId)"

        for ($attempt = 1; $attempt -le $MediaWriteRateLimitMaxRequests; $attempt++) {
            $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$fakeMediaId" -Token $sellerOneToken -Headers $headers
            Require-Status -Response $response -ExpectedStatus 404
        }

        $limitedResponse = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$fakeMediaId" -Token $sellerOneToken -Headers $headers
        Require-Status -Response $limitedResponse -ExpectedStatus 429
        $message = Get-ObjectPropertyValue -Object $limitedResponse.Json -PropertyName "message"
        Require ($message -like "*Too many requests for media-write*") "Expected media-write rate-limit message. Raw body: $($limitedResponse.Body)"
    }
}

if ($script:RunAdminBootstrapTests) {
    Run-Test "Admin can log in through bootstrap account" {
        $body = @{
            email    = $AdminEmail
            password = $AdminPassword
        }
        $response = Invoke-JsonRequest -Method "POST" -Url "$GatewayBaseUrl/auth/login" -Body $body
        if ($response.StatusCode -eq 401) {
            throw "Admin bootstrap login failed. Set ADMIN_EMAIL and ADMIN_PASSWORD before starting user-service, then restart the backend. If that email already exists, bootstrap will not overwrite it."
        }
        Require-Status -Response $response -ExpectedStatus 200
        $token = Get-ObjectPropertyValue -Object $response.Json -PropertyName "token"
        Require (-not [string]::IsNullOrWhiteSpace([string]$token)) "Expected JWT token in admin login response. Raw body: $($response.Body)"
        $script:adminToken = [string]$token
    }

    Run-Test "Admin profile exposes admin role" {
        Require (-not [string]::IsNullOrWhiteSpace($adminToken)) "Admin token is missing"
        $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/me" -Token $adminToken
        Require-Status -Response $response -ExpectedStatus 200
        $email = [string](Get-ObjectPropertyValue -Object $response.Json -PropertyName "email")
        $role = [string](Get-ObjectPropertyValue -Object $response.Json -PropertyName "role")
        Require ($email -eq $AdminEmail) "Expected admin email in /me response. Raw body: $($response.Body)"
        Require ($role -eq "ADMIN") "Expected ADMIN role in /me response. Raw body: $($response.Body)"
    }

    Run-Test "Admin product listing includes seller products" {
        Require (-not [string]::IsNullOrWhiteSpace($adminToken)) "Admin token is missing"
        Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
        $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products/me" -Token $adminToken
        Require-Status -Response $response -ExpectedStatus 200
        $ids = @($response.Json | ForEach-Object { $_.id })
        Require ($ids -contains $productId) "Expected seller product in admin /products/me response"
    }

    Run-Test "Admin media listing includes seller media" {
        Require (-not [string]::IsNullOrWhiteSpace($adminToken)) "Admin token is missing"
        Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
        Require (-not [string]::IsNullOrWhiteSpace($sellerTwoMediaId)) "Seller two media id is missing"
        $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images" -Token $adminToken
        Require-Status -Response $response -ExpectedStatus 200
        $ids = @($response.Json | ForEach-Object { $_.id })
        Require ($ids -contains $mediaId) "Expected seller one media in admin /media/images response"
        Require ($ids -contains $sellerTwoMediaId) "Expected seller two media in admin /media/images response"
    }

    Run-Test "Admin can delete another seller media" {
        Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
        Require (-not [string]::IsNullOrWhiteSpace($adminToken)) "Admin token is missing"

        $uploadResponse = Invoke-MultipartUpload -Url "$GatewayBaseUrl/media/images" -FilePath $files.SmallPng -FileContentType "image/png" -Token $sellerTwoToken
        Require-Status -Response $uploadResponse -ExpectedStatus 201
        $extraMediaId = [string](Get-ObjectPropertyValue -Object $uploadResponse.Json -PropertyName "id")
        Require (-not [string]::IsNullOrWhiteSpace($extraMediaId)) "Expected extra seller two media id for admin moderation test"

        $deleteResponse = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$extraMediaId" -Token $adminToken
        Require-Status -Response $deleteResponse -ExpectedStatus 204

        $downloadResponse = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images/$extraMediaId"
        Require-Status -Response $downloadResponse -ExpectedStatus 404
    }
}

Run-Test "Owner deletes product" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/products/$productId" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 204
}

Run-Test "Deleted product is no longer publicly available" {
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/products/$productId"
    Require-Status -Response $response -ExpectedStatus 404
}

Run-Test "Deleting product clears media product association" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($productId)) "Product id is missing"
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"

    $filteredResponse = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images?productId=$productId" -Token $sellerOneToken
    Require-Status -Response $filteredResponse -ExpectedStatus 200
    $filteredIds = @($filteredResponse.Json | ForEach-Object { $_.id })
    Require (-not ($filteredIds -contains $mediaId)) "Expected deleted product to no longer own media association"

    $allMediaResponse = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images" -Token $sellerOneToken
    Require-Status -Response $allMediaResponse -ExpectedStatus 200
    $allMediaIds = @($allMediaResponse.Json | ForEach-Object { $_.id })
    Require ($allMediaIds -contains $mediaId) "Expected product image to remain available after product deletion"
}

Run-Test "Owner deletes media" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerOneToken)) "Seller one token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$mediaId" -Token $sellerOneToken
    Require-Status -Response $response -ExpectedStatus 204
}

Run-Test "Seller two deletes own unlinked media" {
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoToken)) "Seller two token is missing"
    Require (-not [string]::IsNullOrWhiteSpace($sellerTwoMediaId)) "Seller two media id is missing"
    $response = Invoke-JsonRequest -Method "DELETE" -Url "$GatewayBaseUrl/media/images/$sellerTwoMediaId" -Token $sellerTwoToken
    Require-Status -Response $response -ExpectedStatus 204
}

Run-Test "Deleted media is no longer downloadable" {
    Require (-not [string]::IsNullOrWhiteSpace($mediaId)) "Media id is missing"
    $response = Invoke-JsonRequest -Method "GET" -Url "$GatewayBaseUrl/media/images/$mediaId"
    Require-Status -Response $response -ExpectedStatus 404
}

Write-Host ""
Write-Host "========== SUMMARY ==========" -ForegroundColor Yellow
Write-Host "Passed: $script:PassCount"
Write-Host "Failed: $($script:Failures.Count)"

if ($script:Failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Failures:" -ForegroundColor Yellow
    $index = 1
    foreach ($failure in $script:Failures) {
        Write-Host ("{0}. {1}" -f $index, $failure) -ForegroundColor Red
        $index++
    }
}

if (-not $KeepArtifacts -and (Test-Path $script:TempDirectory)) {
    Remove-Item -LiteralPath $script:TempDirectory -Recurse -Force
} elseif ($KeepArtifacts) {
    Write-Info "Kept artifacts in $script:TempDirectory"
}

$script:HttpClient.Dispose()
$script:HttpClientHandler.Dispose()

if ($script:Failures.Count -gt 0) {
    exit 1
}

exit 0
