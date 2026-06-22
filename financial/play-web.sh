#\!/usr/bin/env bash
# Research-report web playground — config page + live agent progress + report preview.
#   ./financial/play-web.sh            # http://localhost:8088
#   RESEARCH_WEB_PORT=9000 ./financial/play-web.sh
#   TUSHARE_TOKEN=xxxx ./financial/play-web.sh   # use real Tushare A-share data
#   HTTPS_PROXY=http://127.0.0.1:7897 ./financial/play-web.sh   # pull real data via local proxy
#
# The page's "GLM-5.2" model option needs a live LLM endpoint (BANK_LLM_*). This
# script auto-wires it from the global GLM Coding Plan creds: it reads GLM_* from
# the environment, or from ~/.claude/settings.json if not already exported, and
# maps them to the BANK_LLM_* the engine reads. No GLM creds → the page falls back
# to the offline scripted model automatically.
set -euo pipefail
: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home}"
export JAVA_HOME
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"

# Turn an env proxy (HTTPS_PROXY / https_proxy, e.g. http://127.0.0.1:7897) into JVM
# system properties so the data sources' HttpClient (proxy=ProxySelector.getDefault())
# routes through it. exec:java inherits MAVEN_OPTS.
PROXY_URL="${HTTPS_PROXY:-${https_proxy:-}}"
if [ -n "$PROXY_URL" ]; then
  # strip scheme, then split host:port (default port 80 when absent).
  hostport="${PROXY_URL#*://}"
  hostport="${hostport%%/*}"
  proxy_host="${hostport%%:*}"
  if [ "$hostport" = "$proxy_host" ]; then
    proxy_port=80
  else
    proxy_port="${hostport##*:}"
  fi
  if [ -n "$proxy_host" ]; then
    MAVEN_OPTS="${MAVEN_OPTS:-} -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port -Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port"
    export MAVEN_OPTS
    echo "[play-web] proxy -> $proxy_host:$proxy_port (via MAVEN_OPTS)" >&2
  fi
fi
# Pull the global GLM Coding Plan creds from ~/.claude/settings.json when not already
# in the environment (they live there for all local projects), then map GLM_* → the
# BANK_LLM_* the engine reads so the live "GLM-5.2" option works out of the box.
SETTINGS="$HOME/.claude/settings.json"
if [ -f "$SETTINGS" ]; then
  eval "$(python3 - "$SETTINGS" <<'PY'
import json, sys, shlex, os
try:
    env = json.load(open(sys.argv[1])).get("env", {})
    # Only export when not already set in the shell (shell wins).
    for k in ("GLM_API_KEY", "GLM_BASE_URL", "GLM_MODEL",
              "DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"):
        if env.get(k) and not os.environ.get(k):
            print(f'export {k}={shlex.quote(env[k])}')
except Exception:
    pass
PY
)"
fi
if [ -n "${GLM_API_KEY:-}" ] && [ -n "${GLM_BASE_URL:-}" ]; then
  export BANK_LLM_PROVIDER="${BANK_LLM_PROVIDER:-openai}"
  export BANK_LLM_API_BASE="${BANK_LLM_API_BASE:-$GLM_BASE_URL}"
  export BANK_LLM_API_KEY="${BANK_LLM_API_KEY:-$GLM_API_KEY}"
  export BANK_LLM_MODEL="${BANK_LLM_MODEL:-${GLM_MODEL:-glm-5.2}}"
  echo "[play-web] live model -> $BANK_LLM_MODEL (via $BANK_LLM_API_BASE)" >&2
else
  echo "[play-web] no GLM creds found — the GLM option will fall back to the scripted model" >&2
fi

./mvnw -q -o -f financial/pom.xml \
  -Dexec.mainClass=com.bank.financial.research.web.ResearchWebServer \
  exec:java
