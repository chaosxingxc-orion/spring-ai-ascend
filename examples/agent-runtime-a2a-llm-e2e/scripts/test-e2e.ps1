# Load an env file, install agent-runtime into the local Maven repo, then run the
# example A2A + AgentScope E2E suite (real-LLM tests run when SAA_SAMPLE_LLM_API_KEY
# is set).
#
# Usage: ./scripts/test-e2e.ps1 [-EnvFile .env]
#   ./scripts/test-e2e.ps1 -EnvFile .env.ollama.example
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
    Write-Host "env file not found: $EnvFile - using process env / application.yaml defaults"
}
if ([string]::IsNullOrWhiteSpace($env:SAA_SAMPLE_LLM_API_KEY)) {
    Write-Host "WARNING: SAA_SAMPLE_LLM_API_KEY is blank - the real-LLM e2e branch will be SKIPPED (assumeTrue)."
}
Set-Location $repo
& ./mvnw -pl agent-runtime -am install -DskipTests -Dmaven.test.skip=true
# The example pom defaults skipTests=true for reactor hygiene; this script's whole
# purpose is to run the suite, so the override is mandatory here.
& ./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test "-DskipTests=false" | Tee-Object -Variable mvnLog | Out-Host
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
if ($mvnLog -match 'Tests are skipped\.') {
    Write-Error "surefire skipped the tests - the E2E suite did not run."
    exit 1
}
