#!/usr/bin/env bash
# Wave 1 advisory gate for the Structurizr workspace authority migration.
#
# Authority: ADR-0147 (Structurizr Workspace Authority).
#
# Wave 1 mode: ADVISORY. The script reports violations but exits 0 even when
# the profile validator finds them. Wave 5 flips this to blocking — the
# script begins to exit non-zero on profile violations or byte-drift in
# the generated zone.
#
# Invocation (run via WSL per Rule G-7):
#   wsl -d Ubuntu -- bash -lc "cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_architecture_workspace.sh"
#
# Or directly inside a Linux/WSL shell:
#   bash gate/check_architecture_workspace.sh

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$REPO_ROOT/architecture/workspace.dsl"
TOOL_POM="$REPO_ROOT/tools/architecture-workspace/pom.xml"
NORMALIZED_OUT="$REPO_ROOT/out/architecture/normalized-model.json"

# Wave 5: BLOCKING mode (default). Override with
# ARCHITECTURE_WORKSPACE_ADVISORY=1 ARCHITECTURE_WORKSPACE_BLOCKING=0 for
# rollback-style runs during the 14-day soak (per the migration plan).
ADVISORY="${ARCHITECTURE_WORKSPACE_ADVISORY:-0}"
BLOCKING="${ARCHITECTURE_WORKSPACE_BLOCKING:-1}"

red()    { printf '\033[31m%s\033[0m\n' "$1"; }
green()  { printf '\033[32m%s\033[0m\n' "$1"; }
yellow() { printf '\033[33m%s\033[0m\n' "$1"; }
note()   { printf '%s\n' "$1"; }

if [[ ! -f "$WORKSPACE" ]]; then
  yellow "ARCHITECTURE WORKSPACE: workspace.dsl missing at $WORKSPACE — Wave 1 advisory PASS (skeleton may be pending)"
  exit 0
fi

if [[ ! -f "$TOOL_POM" ]]; then
  yellow "ARCHITECTURE WORKSPACE: tools/architecture-workspace pom missing — Wave 1 advisory PASS (tooling pending)"
  exit 0
fi

mkdir -p "$REPO_ROOT/out/architecture"

note "ARCHITECTURE WORKSPACE: running ProfileValidator tests..."
mvn_exit=0
# `clean` first to avoid Windows/WSL cross-platform file-ownership conflicts
# in target/ when the Windows-side build had previously written there.
( cd "$REPO_ROOT" && ./mvnw -B -q -f "$TOOL_POM" clean test ) || mvn_exit=$?

if [[ $mvn_exit -ne 0 ]]; then
  if [[ "$ADVISORY" == "1" ]]; then
    yellow "ARCHITECTURE WORKSPACE (ADVISORY): tool tests failed (exit $mvn_exit) — would block at Wave 5"
    exit 0
  else
    red "ARCHITECTURE WORKSPACE: tool tests failed (exit $mvn_exit)"
    exit $mvn_exit
  fi
fi

note "ARCHITECTURE WORKSPACE: validating workspace.dsl against profile..."
validate_exit=0
( cd "$REPO_ROOT" && ./mvnw -B -q -f "$TOOL_POM" exec:java -Dexec.args="validate $WORKSPACE" ) || validate_exit=$?

if [[ $validate_exit -ne 0 ]]; then
  if [[ "$ADVISORY" == "1" ]]; then
    yellow "ARCHITECTURE WORKSPACE (ADVISORY): profile violations present (exit $validate_exit) — would block at Wave 5"
    exit 0
  else
    red "ARCHITECTURE WORKSPACE: profile violations (exit $validate_exit)"
    exit $validate_exit
  fi
fi

# W3 fragment idempotency check (architecture/generated/*.dsl byte-identical).
FRAGMENT_CHECK="$REPO_ROOT/gate/lib/check_workspace_fragment_idempotency.py"
if [[ -f "$FRAGMENT_CHECK" ]]; then
  note "ARCHITECTURE WORKSPACE: checking architecture/generated/* idempotency..."
  frag_exit=0
  ( cd "$REPO_ROOT" && python3 "$FRAGMENT_CHECK" ) || frag_exit=$?
  if [[ $frag_exit -ne 0 ]]; then
    if [[ "$BLOCKING" == "1" ]]; then
      red "ARCHITECTURE WORKSPACE: generated-zone drift (exit $frag_exit)"
      exit $frag_exit
    else
      yellow "ARCHITECTURE WORKSPACE (ADVISORY): generated-zone drift (exit $frag_exit) — would block at Wave 5"
      exit 0
    fi
  fi
fi

# W4 reverse projection (workspace -> compatibility graph YAML).
note "ARCHITECTURE WORKSPACE: emitting compatibility graph projection..."
project_exit=0
( cd "$REPO_ROOT" && ./mvnw -B -q -f "$TOOL_POM" exec:java \
    -Dexec.args="project $WORKSPACE docs/governance/architecture-workspace-graph.yaml" ) || project_exit=$?
if [[ $project_exit -ne 0 ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    red "ARCHITECTURE WORKSPACE: projection emission failed (exit $project_exit)"
    exit $project_exit
  else
    yellow "ARCHITECTURE WORKSPACE (ADVISORY): projection emission failed (exit $project_exit) — would block at Wave 5"
    exit 0
  fi
fi

# Round-3 Wave Alpha (sweep defect 17): workspace baseline parity gate.
# Compares architecture-status.yaml#baseline_metrics.workspace_elements
# / workspace_relationships against the live projection counts in
# architecture-workspace-graph.yaml. Drift fails closed.
PARITY_SCRIPT="$REPO_ROOT/gate/lib/check_workspace_baseline_parity.py"
if [[ -f "$PARITY_SCRIPT" ]]; then
  note "ARCHITECTURE WORKSPACE: checking workspace baseline parity..."
  if ! ( cd "$REPO_ROOT" && python3 "$PARITY_SCRIPT" ); then
    red "ARCHITECTURE WORKSPACE: workspace baseline parity FAILED -- update docs/governance/architecture-status.yaml#baseline_metrics to match the live projection counts"
    exit 1
  fi
fi

# W4 informational comparison against legacy graph (does not gate).
# Round-3 Wave Alpha (2026-05-28 sweep defect 12): the previous `|| true`
# suffix silently lost non-zero exits from the comparison script — same
# fail-open class as the Rule 131 R1 case. Replaced with an explicit
# `if !` form that still keeps the check informational by emitting an
# ADVISORY line on failure instead of propagating the exit code, but
# the failure is now visible in gate output rather than masked.
COMPARE_SCRIPT="$REPO_ROOT/gate/lib/compare_workspace_to_legacy_graph.py"
if [[ -f "$COMPARE_SCRIPT" ]]; then
  note "ARCHITECTURE WORKSPACE: comparing projection to legacy graph (informational)..."
  if ! ( cd "$REPO_ROOT" && python3 "$COMPARE_SCRIPT" ); then
    note "ADVISORY: legacy-graph comparison reported non-zero exit (informational, not blocking)"
  fi
fi

if [[ "$BLOCKING" == "1" ]]; then
  green "ARCHITECTURE WORKSPACE: PASS (W5+ blocking mode — ADR-0147)"
else
  green "ARCHITECTURE WORKSPACE: PASS (advisory mode — set ARCHITECTURE_WORKSPACE_BLOCKING=1 for blocking)"
fi
exit 0
