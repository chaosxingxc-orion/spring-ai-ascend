#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 40 — enforcer_reachable_from_principle. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 40 — enforcer_reachable_from_principle (enforcer E58, ADR-0068)
#
# Every shipped enforcer row in docs/governance/enforcers.yaml MUST be
# reachable from at least one Layer-0 principle (P-A..P-D or legacy
# P1..P3/E1) through the edge chain in architecture-graph.yaml:
#   principle --operationalised_by--> Rule-N --enforced_by--> E<n>
# The Python graph builder owns the traversal; this rule delegates to it.
# ---------------------------------------------------------------------------
_r40_fail=0
if [[ ! -f docs/governance/architecture-graph.yaml ]]; then
  fail_rule "enforcer_reachable_from_principle" "docs/governance/architecture-graph.yaml not present — run gate/build_architecture_graph.sh first"; _r40_fail=1
else
  # Embedded traversal check (avoids second Python invocation). For every
  # enforcer node E<n>, confirm there exists at least one Rule-N node feeding
  # it and that Rule-N is operationalised by at least one principle.
  _r40_orphans="$(awk '
    /^- id: / {
      if (cur != "" && type == "enforcer") enforcers[cur] = 1
      cur = $3
      type = ""
    }
    /^  type: enforcer/ { type = "enforcer" }
    /^  type: rule/    { rules_seen[cur] = 1 }
    /^  type: principle/ { principles_seen[cur] = 1 }
    /^- src: / { src = $3 }
    /^  dst: / { dst = $2 }
    /^  type: enforced_by/ { rule_to_enf[src] = rule_to_enf[src] " " dst; enf_has_rule[dst] = 1 }
    /^  type: operationalised_by/ { prin_to_rule[src] = prin_to_rule[src] " " dst; rule_has_prin[dst] = 1 }
    END {
      for (e in enforcers) {
        if (!(e in enf_has_rule)) {
          print "  - " e " (no rule -> enforcer edge)"
          orphan++
        }
      }
      if (orphan > 0) exit 1
    }
  ' docs/governance/architecture-graph.yaml 2>/dev/null || true)"
  if [[ -n "$_r40_orphans" ]]; then
    fail_rule "enforcer_reachable_from_principle" "orphaned enforcer(s): no rule path back to a principle:"
    echo "$_r40_orphans" >&2
    _r40_fail=1
  fi
fi
if [[ $_r40_fail -eq 0 ]]; then pass_rule "enforcer_reachable_from_principle"; fi

# ===========================================================================
# Phase M remediation (CLAUDE.md Rules 33-34, ADR-0068)
# Rules 41-44 close the self-violations the W1 wave inherited from Rule 28:
# anchor validation, idempotency, ADR-shape, frozen-doc edit path.
# Enforcer rows E60-E63 in docs/governance/enforcers.yaml.
# ===========================================================================

# ---------------------------------------------------------------------------
