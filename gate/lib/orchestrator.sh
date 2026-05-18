#!/usr/bin/env bash
# gate/lib/orchestrator.sh — thin compatibility shim.
#
# History: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md) introduced a
# parallel-per-rule-file architecture. The production parallel gate has since
# converged on `gate/check_parallel.sh` (which extracts rule bodies from the
# canonical monolith at runtime + does richer NDJSON logging + parity-checks
# against the serial canonical script per Rule 88). This file remains as a
# stable entry-point for any caller that targeted it directly; it now simply
# delegates to `gate/check_parallel.sh` and the per-rule files under
# `gate/rules/` are NO LONGER consumed by either canonical or parallel
# production gate.
#
# The `gate/rules/` directory is a **generated artifact for IDE inspection and
# code review only** (rc9 / 2026-05-19 / ADR-0083). It is refreshed by
# `gate/lib/extract_rules.sh` and its freshness against the canonical monolith
# is asserted by Rule 92 (`gate_rules_corpus_freshness`). Do not hand-edit;
# do not assume production gate output reflects edits here.

set -uo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

RULES_DIR="gate/rules"
JOBS="${GATE_JOBS:-8}"

if [[ ! -d "$RULES_DIR" ]]; then
  echo "FAIL: orchestrator -- $RULES_DIR missing; run gate/lib/extract_rules.sh first" >&2
  exit 1
fi

# Delegate to the production parallel wrapper. The per-rule files under
# gate/rules/ are IDE-only generated artifacts (rc9 / ADR-0083); production
# gate output comes from the canonical monolith via `check_parallel.sh`.
exec bash "$repo_root/gate/check_parallel.sh" "$@"
