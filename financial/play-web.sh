#\!/usr/bin/env bash
# Research-report web playground — config page + live agent progress + report preview.
#   ./financial/play-web.sh            # http://localhost:8088
#   RESEARCH_WEB_PORT=9000 ./financial/play-web.sh
#   TUSHARE_TOKEN=xxxx ./financial/play-web.sh   # use real Tushare A-share data
#   HTTPS_PROXY=http://127.0.0.1:7897 ./financial/play-web.sh   # pull real data via local proxy
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
./mvnw -q -o -f financial/pom.xml \
  -Dexec.mainClass=com.bank.financial.research.web.ResearchWebServer \
  exec:java
