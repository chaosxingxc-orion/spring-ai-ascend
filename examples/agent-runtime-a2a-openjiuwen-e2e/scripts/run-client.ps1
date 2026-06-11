# Load an env file, then run the OpenJiuwen-only A2A console client against a running server.
# Start the server first (run-server.ps1). Usage: ./scripts/run-client.ps1 [-EnvFile .env]
param([string]$EnvFile = "$PSScriptRoot\..\.env")
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..\..\..").Path
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
    }
    Write-Host "loaded env: $EnvFile"
} else {
    Write-Host "env file not found: $EnvFile - using process env defaults"
}
Set-Location $repo
& ./mvnw -f examples/agent-runtime-a2a-openjiuwen-e2e/pom.xml exec:java -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
