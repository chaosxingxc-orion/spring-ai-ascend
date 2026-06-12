# Load an env file, install agent-runtime, then start the example A2A + AgentScope server.
# Usage: ./scripts/run-server.ps1 [-EnvFile .env]
param([string]$EnvFile = "$PSScriptRoot\..\.env")
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..\..\..").Path
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
    }
    Write-Host "loaded env: $EnvFile  (apiBase=$env:SAA_SAMPLE_AGENTSCOPE_API_BASE model=$env:SAA_SAMPLE_LLM_MODEL)"
} else {
    Write-Host "env file not found: $EnvFile - using application.yaml defaults"
}
Set-Location $repo
& ./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
& ./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
