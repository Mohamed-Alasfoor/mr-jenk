param(
    [int[]]$Ports = @(8761, 8080, 8081, 8082, 8083),
    [switch]$IncludeDiagnostics,
    [switch]$ListOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

if ($IncludeDiagnostics) {
    $Ports += 18081, 18082, 18083
}

$Ports = $Ports | Sort-Object -Unique
$portPattern = ":" + (($Ports | ForEach-Object { [string]$_ }) -join "|:")

$netstatOutput = & netstat -ano -p tcp
$matchingLines = $netstatOutput | Where-Object {
    $_ -match "^\s*TCP\s+\S+\s+\S+\s+LISTENING\s+\d+\s*$" -and $_ -match $portPattern
}

$processIds = New-Object System.Collections.Generic.HashSet[int]
foreach ($line in $matchingLines) {
    $parts = ($line -split "\s+") | Where-Object { $_ -ne "" }
    if ($parts.Count -lt 5) {
        continue
    }

    $localAddress = $parts[1]
    $processId = 0
    if (-not [int]::TryParse($parts[4], [ref]$processId)) {
        continue
    }

    foreach ($port in $Ports) {
        if ($localAddress -match (":{0}$" -f $port)) {
            [void]$processIds.Add($processId)
            break
        }
    }
}

if ($processIds.Count -eq 0) {
    Write-Info "No listening backend processes were found on ports: $($Ports -join ', ')"
    exit 0
}

$targets = foreach ($targetProcessId in ($processIds | Sort-Object)) {
    try {
        $process = Get-Process -Id $targetProcessId -ErrorAction Stop
        [pscustomobject]@{
            Id   = $process.Id
            Name = $process.ProcessName
        }
    } catch {
        [pscustomobject]@{
            Id   = $targetProcessId
            Name = "<exited>"
        }
    }
}

$targets | Format-Table -AutoSize | Out-Host

if ($ListOnly) {
    Write-Info "Listed backend listener processes only."
    exit 0
}

foreach ($target in $targets) {
    if ($target.Name -eq "<exited>") {
        continue
    }

    try {
        Stop-Process -Id $target.Id -Force -ErrorAction Stop
        Write-Info "Stopped PID $($target.Id) ($($target.Name))"
    } catch {
        Write-Warn "Failed to stop PID $($target.Id) ($($target.Name)): $($_.Exception.Message)"
    }
}
