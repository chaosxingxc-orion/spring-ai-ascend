$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Start-Stub {
    param(
        [string]$Module,
        [int]$Port
    )
    $jar = Get-ChildItem -Path "$Module\target\*-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jar) {
        Write-Host "skip ${Module}: jar not found (build stub module first)" -ForegroundColor Yellow
        return $false
    }
    $log = Join-Path $env:TEMP "deep-research-$Module.log"
    Start-Process java -ArgumentList @(
        "-jar", $jar.FullName,
        "--spring.profiles.active=stub",
        "--server.port=$Port"
    ) -RedirectStandardOutput $log -RedirectStandardError $log -NoNewWindow
    Write-Host "started $Module on $Port (log: $log)"
    return $true
}

$ok = @(
    (Start-Stub "agent-search-a2a" 13004),
    (Start-Stub "agent-read-a2a" 13005),
    (Start-Stub "agent-verify-a2a" 13006)
) | Where-Object { $_ } | Measure-Object | Select-Object -ExpandProperty Count

if ($ok -lt 3) {
    Write-Host "Some stubs were not started. Build B/C/D modules first, then re-run." -ForegroundColor Red
    exit 1
}

Write-Host "stubs started: search=13004 read=13005 verify=13006"
