#!/usr/bin/env bash
# spring-ai-ascend Rule 8 operator-shape smoke gate -- POSIX entry point.
#
# Per CLAUDE.md / AGENTS.md Rule 8 and
# docs/systematic-architecture-remediation-plan-2026-05-08-cycle-4.en.md sec-D1.
#
# Currently fails closed because the runnable artifact does not exist
# (W0 has not landed yet). When W0 produces the Maven multi-module + minimal
# Spring Boot, this script will be replaced with the real smoke flow:
#
#   1. build the runnable artifact (mvn -q package)
#   2. start a long-lived managed process
#   3. use real local Postgres
#   4. hit /health and /ready
#   5. perform N>=3 sequential POST /v1/runs
#   6. prove resource reuse + lifecycle observability
#   7. cancel a live run and drive it terminal (200)
#   8. cancel an unknown run -> 404
#   9. assert *_fallback_total == 0 on the happy path
#  10. write gate/log/operator-shape/<sha>.json with evidence_valid_for_delivery=true
#  11. write docs/delivery/<date>-<sha>.md
#
# Until then, the script writes a fail-closed artifact-missing log under
# gate/log/local/ (gitignored) and exits 1.
#
# There is NO --local-only mode for the operator-shape gate. Dirty trees are
# never valid Rule 8 evidence.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

sha_candidate="$(git rev-parse --short HEAD 2>/dev/null || echo no-git)"
[[ -z "$sha_candidate" ]] && sha_candidate=no-git

# Cycle-13 (Phase B step 1): probe what's in tree to determine the
# fail-closed reason.
#   - No pom.xml + no src           -> FAIL_ARTIFACT_MISSING (pre-W0).
#   - pom.xml + src exist but no JAR -> FAIL_NEEDS_BUILD (cycle-13 partial; user
#                                       runs `mvn package` to advance).
#   - JAR exists but no real-flow run -> FAIL_NEEDS_REAL_FLOW (W0..W4 in flight).
#   - real-flow run pending          -> W4 deliverable per Rule 8.

declare -a manifest_probes=(
  "pom.xml|no Maven build manifest at repo root"
  "agent-service/pom.xml|no Maven build manifest under agent-service/"
  "agent-runtime-core/pom.xml|no Maven build manifest under agent-runtime-core/"
)
declare -a source_probes=(
  "agent-service/src/main/java|no source tree under agent-service/"
  "agent-runtime-core/src/main/java|no source tree under agent-runtime-core/"
)
missing_json=""
missing_count=0
manifests_present=true
sources_present=true
for entry in "${manifest_probes[@]}"; do
  path="${entry%%|*}"
  reason="${entry##*|}"
  if [[ ! -e "$path" ]]; then
    if [[ -n "$missing_json" ]]; then missing_json+=","; fi
    missing_json+="{\"path\":\"$path\",\"reason\":\"$reason\"}"
    missing_count=$((missing_count + 1))
    manifests_present=false
  fi
done
for entry in "${source_probes[@]}"; do
  path="${entry%%|*}"
  reason="${entry##*|}"
  if [[ ! -e "$path" ]]; then
    if [[ -n "$missing_json" ]]; then missing_json+=","; fi
    missing_json+="{\"path\":\"$path\",\"reason\":\"$reason\"}"
    missing_count=$((missing_count + 1))
    sources_present=false
  fi
done

# Probe for a built JAR (any version under agent-service/target/).
jar_present=false
if compgen -G "agent-service/target/agent-service-*.jar" > /dev/null 2>&1; then
  jar_present=true
fi

# Determine outcome state.
if [[ "$manifests_present" == "false" || "$sources_present" == "false" ]]; then
  outcome="FAIL_ARTIFACT_MISSING"
  message="Rule 8 operator-shape smoke gate fails closed: pom.xml or src tree missing. Pre-cycle-13 state. Architecture-sync evidence does NOT substitute for Rule 8 evidence."
elif [[ "$jar_present" == "false" ]]; then
  outcome="FAIL_NEEDS_BUILD"
  message="Rule 8 operator-shape smoke gate fails closed: pom.xml + src present but no built JAR under agent-service/target/. Run 'mvn -B -pl agent-service -am package' to advance. Real Rule 8 flow (long-lived process + N>=3 real-dependency runs) remains a W4 deliverable."
else
  outcome="FAIL_NEEDS_REAL_FLOW"
  message="Rule 8 operator-shape smoke gate fails closed: JAR exists but no real-flow run yet. Real Rule 8 flow (long-lived process + real dependencies + sequential N>=3 + lifecycle observability + cancellation round-trip + zero fallback) remains a W4 deliverable. Architecture-sync evidence does NOT substitute for Rule 8 evidence."
fi

artifact_present=true
[[ "$manifests_present" == "false" || "$sources_present" == "false" ]] && artifact_present=false

log_dir="gate/log/local"
mkdir -p "$log_dir"
log_path="$log_dir/operator-shape-${sha_candidate}-posix.json"

{
  printf '{'
  printf '"script":"run_operator_shape_smoke.sh",'
  printf '"version":"cycle-13-tri-state",'
  printf '"kind":"operator_shape_smoke",'
  printf '"sha":"%s",' "$sha_candidate"
  printf '"generated":"%s",' "$(date -Iseconds 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '"manifests_present":%s,' "$manifests_present"
  printf '"sources_present":%s,' "$sources_present"
  printf '"jar_present":%s,' "$jar_present"
  printf '"artifact_present":%s,' "$artifact_present"
  printf '"missing_artifacts":[%s],' "$missing_json"
  printf '"outcome":"%s",' "$outcome"
  printf '"evidence_valid_for_delivery":false,'
  printf '"rule_8_evidence":false,'
  printf '"message":"%s"' "$message"
  printf '}\n'
} > "$log_path"

echo "FAIL ($outcome): operator-shape smoke gate. Log: $log_path" >&2
cat "$log_path"
exit 1
