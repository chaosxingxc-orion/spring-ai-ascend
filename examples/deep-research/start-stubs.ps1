$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Start-Stub {
    param(
        [string]$Module,
        [int]$Port,
        [switch]$Optional
    )
    $jar = Get-ChildItem -Path "$Module\target\*-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jar) {
        if ($Optional) {
            Write-Host "skip ${Module}: jar not found (optional; read sub-agent not in repo yet)" -ForegroundColor Yellow
        } else {
            Write-Host "skip ${Module}: jar not found (build stub module first)" -ForegroundColor Yellow
        }
        return $false
    }
    $logOut = Join-Path $env:TEMP "deep-research-$Module.out.log"
    $logErr = Join-Path $env:TEMP "deep-research-$Module.err.log"
    Start-Process java -ArgumentList @(
        "-jar", $jar.FullName,
        "--spring.profiles.active=stub",
        "--server.port=$Port"
    ) -RedirectStandardOutput $logOut -RedirectStandardError $logErr -NoNewWindow
    Write-Host "started $Module on $Port (stdout: $logOut, stderr: $logErr)"
    return $true
}

$searchOk = Start-Stub "agent-search-a2a" 13004
$readOk = Start-Stub "agent-read-a2a" 13005 -Optional
$verifyOk = Start-Stub "agent-verify-a2a" 13006

if (-not $searchOk -or -not $verifyOk) {
    Write-Host "Required stubs (search + verify) were not started. Build modules first:" -ForegroundColor Red
    Write-Host "  .\mvnw.cmd -f examples\deep-research\pom.xml package -DskipTests" -ForegroundColor Red
    exit 1
}

if (-not $readOk) {
    Write-Host "note: plan_read stub (13005) not started — agent-read-a2a is not in the repo yet; MCP/SkillHub tests still work." -ForegroundColor Yellow
}

Write-Host "stubs started: search=13004 verify=13006$(if ($readOk) { ' read=13005' } else { '' })"
