#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VIEW_ROOT="$ROOT/docs/architecture-views"
COMMON_DIR="$VIEW_ROOT/plantuml/common"
L0_DIR="$VIEW_ROOT/plantuml/l0"

log() {
  echo "[architecture-views-gate] $*"
}

fail() {
  echo "[architecture-views-gate] ERROR: $*" >&2
  exit 1
}

# Fail closed when ripgrep is missing — the whole gate is built on it and
# silently passing on rg-less hosts would let Mermaid sources, deleted-name
# leakage, and retired-label drift past this check.
command -v rg >/dev/null 2>&1 || fail "ripgrep (rg) is required on PATH; install it before running this gate."

MERMAID_SCAN_TMP="$(mktemp -t architecture-view-mermaid.XXXXXX)"
trap 'rm -f "$MERMAID_SCAN_TMP"' EXIT

required_files=(
  "$VIEW_ROOT/README.md"
  "$COMMON_DIR/theme.puml"
  "$COMMON_DIR/links.puml"
  "$COMMON_DIR/l0-elements.puml"
  "$L0_DIR/l0-scenario.puml"
  "$L0_DIR/l0-logical.puml"
  "$L0_DIR/l0-development.puml"
  "$L0_DIR/l0-process.puml"
  "$L0_DIR/l0-physical.puml"
)

log "Checking required architecture view files."
for file in "${required_files[@]}"; do
  [[ -f "$file" ]] || fail "missing required file: ${file#$ROOT/}"
done

log "Checking that Mermaid is not used as a source format."
if rg -n '```mermaid|\.mmd\b|mermaid\.js' "$VIEW_ROOT" >"$MERMAID_SCAN_TMP"; then
  cat "$MERMAID_SCAN_TMP" >&2
  fail "Mermaid source or dependency found under docs/architecture-views."
fi

log "Checking that lower-level terms are not promoted to L0 component definitions."
component_pattern='^[[:space:]]*(Person|System|System_Ext|Container|ContainerDb|Component|ComponentDb|Deployment_Node|Node)\('
for term in TaskCursor HydrationRequest YieldResponse ResumeEnvelope WorkflowIntermediary Mailbox BackpressureSignal Checkpointer RunRepository Orchestrator GraphExecutor AgentLoopExecutor SuspendSignal TCK; do
  if rg -n "$component_pattern.*$term" "$L0_DIR"; then
    fail "lower-level term appears in a L0 component definition: $term"
  fi
done

log "Checking that L0 diagrams do not use the retired capability label."
if rg -n 'Agent Runtime' "$L0_DIR"; then
  fail 'L0 PlantUML sources must use Agent Service, not Agent Runtime.'
fi

log "Checking required L0 capability labels."
for label in "Agent Client" "Agent Service" "Agent Execution Engine" "Agent Bus" "Agent Middleware" "Agent Evolution Layer"; do
  if ! rg -q "$label" "$COMMON_DIR/l0-elements.puml" "$L0_DIR"; then
    fail "missing L0 capability label: $label"
  fi
done

log "Rendering PlantUML sources in check mode."
bash "$ROOT/scripts/render-architecture-views.sh" --check

log "Architecture view gate passed."
