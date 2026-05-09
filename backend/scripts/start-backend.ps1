param(
    [switch]$Clean,
    [switch]$StopExisting,
    [switch]$SkipMongo,
    [switch]$SkipObjectStorage,
    [switch]$UseHttps,
    [string]$SslKeyStorePath = (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\certs\dev-services.p12"),
    [string]$SslKeyStorePassword = "changeit",
    [string]$SslKeyAlias = "buy01-dev"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDirectory = Split-Path -Parent $scriptDirectory
$powershellExe = Join-Path $PSHOME "powershell.exe"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Assert-CommandExists {
    param([string]$Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Start-ServiceWindow {
    param(
        [string]$ModuleName,
        [string]$WindowTitle
    )

    $commandParts = @(
        '$host.UI.RawUI.WindowTitle = ''' + $WindowTitle + '''',
        'Set-Location ''' + $backendDirectory + ''''
    )

    if ($UseHttps) {
        $resolvedKeyStorePath = [System.IO.Path]::GetFullPath($SslKeyStorePath)
        $javaToolOptions = '-Djavax.net.ssl.trustStore="' + $resolvedKeyStorePath + '" ' +
                '-Djavax.net.ssl.trustStorePassword=' + $SslKeyStorePassword + ' ' +
                '-Djavax.net.ssl.trustStoreType=PKCS12'

        $commandParts += @(
            '$env:SERVER_SSL_ENABLED=''true''',
            '$env:SERVER_SSL_KEY_STORE=''' + $resolvedKeyStorePath + '''',
            '$env:SERVER_SSL_KEY_STORE_PASSWORD=''' + $SslKeyStorePassword + '''',
            '$env:SERVER_SSL_KEY_STORE_TYPE=''PKCS12''',
            '$env:SERVER_SSL_KEY_ALIAS=''' + $SslKeyAlias + '''',
            '$env:EUREKA_NON_SECURE_PORT_ENABLED=''false''',
            '$env:EUREKA_DEFAULT_ZONE=''https://localhost:8761/eureka''',
            '$env:MEDIA_SERVICE_INTERNAL_BASE_URL=''https://localhost:8083''',
            '$env:PRODUCT_SERVICE_INTERNAL_BASE_URL=''https://localhost:8082''',
            '$env:MEDIA_PUBLIC_BASE_URL=''https://localhost:8080/media/images''',
            '$env:ALLOWED_ORIGINS=''https://localhost:4200''',
            '$env:JAVA_TOOL_OPTIONS=''' + $javaToolOptions + ''''
        )
    }

    $commandParts += 'mvn -pl ' + $ModuleName + ' spring-boot:run'
    $command = $commandParts -join '; '

    Start-Process -FilePath $powershellExe `
        -WorkingDirectory $backendDirectory `
        -ArgumentList @('-NoExit', '-Command', $command) | Out-Null
}

Assert-CommandExists -Name "mvn"

if ($StopExisting) {
    $stopScript = Join-Path $scriptDirectory "stop-backend.ps1"
    Write-Info "Stopping any backend processes already listening on service ports"
    & $powershellExe -ExecutionPolicy Bypass -File $stopScript
}

if ($UseHttps) {
    $generateCertScript = Join-Path $scriptDirectory "generate-dev-certs.ps1"
    if (-not (Test-Path $SslKeyStorePath)) {
        Write-Info "Generating development TLS certificate"
        & $powershellExe -ExecutionPolicy Bypass -File $generateCertScript `
            -OutputDirectory (Split-Path -Parent $SslKeyStorePath) `
            -StorePassword $SslKeyStorePassword `
            -Alias $SslKeyAlias | Out-Null
    }
}

if (-not $SkipMongo -or -not $SkipObjectStorage) {
    Assert-CommandExists -Name "docker"
    Write-Info "Starting infrastructure with Docker Compose"
    Push-Location $backendDirectory
    try {
        $infrastructureServices = @()
        if (-not $SkipMongo) {
            $infrastructureServices += "mongo"
        }
        if (-not $SkipObjectStorage) {
            $infrastructureServices += "minio"
        }

        if ($infrastructureServices.Count -gt 0) {
            docker compose up -d @infrastructureServices | Out-Null
        }
    } finally {
        Pop-Location
    }
}

if ($Clean) {
    Write-Info "Cleaning all backend modules"
    Push-Location $backendDirectory
    try {
        mvn clean
    } finally {
        Pop-Location
    }
}

Write-Info "Starting discovery-service"
Start-ServiceWindow -ModuleName "discovery-service" -WindowTitle "buy01-discovery-service"

Write-Info "Waiting 6 seconds before starting dependent services"
Start-Sleep -Seconds 6

$services = @(
    @{ Module = "gateway-service"; Title = "buy01-gateway-service" },
    @{ Module = "user-service"; Title = "buy01-user-service" },
    @{ Module = "product-service"; Title = "buy01-product-service" },
    @{ Module = "media-service"; Title = "buy01-media-service" }
)

foreach ($service in $services) {
    Write-Info ("Starting " + $service.Module)
    Start-ServiceWindow -ModuleName $service.Module -WindowTitle $service.Title
}

Write-Info "All service windows were launched."
Write-Info "Use -Clean when you want to clear stale target classes before startup."
if ($UseHttps) {
    Write-Info "Services were started with HTTPS enabled."
}
