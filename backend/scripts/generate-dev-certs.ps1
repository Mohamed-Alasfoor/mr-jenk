param(
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\certs"),
    [string]$StorePassword = "changeit",
    [string]$Alias = "buy01-dev"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$keystorePath = Join-Path $resolvedOutputDirectory "dev-services.p12"

function Assert-CommandExists {
    param([string]$Name)

    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

Assert-CommandExists -Name "keytool"
New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force | Out-Null

if (-not (Test-Path $keystorePath)) {
    & keytool `
        -genkeypair `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -storetype PKCS12 `
        -keystore $keystorePath `
        -storepass $StorePassword `
        -keypass $StorePassword `
        -validity 3650 `
        -dname "CN=localhost, OU=Development, O=Buy01, L=Local, ST=Local, C=US" `
        -ext "SAN=dns:localhost,ip:127.0.0.1" | Out-Null
}

Write-Host $keystorePath
