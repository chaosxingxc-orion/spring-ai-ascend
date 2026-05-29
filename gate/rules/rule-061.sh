#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 61 — legacy_powershell_gate_deprecated. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 61 — legacy_powershell_gate_deprecated (v2.0.0-rc2 / second-pass review P0-1)
#
# The PowerShell architecture-sync gate (gate/check_architecture_sync.ps1) was
# frozen at Rule 29 in 2026-05 while the bash gate evolved to Rule 60+. The
# second-pass review (docs/logs/reviews/2026-05-16-l0-w2x-rc1-second-pass-architecture-review.en.md
# §P0-1) required choosing one of two postures. v2.0.0-rc2 picked the
# canonical-bash posture per the response document. This rule asserts BOTH
# halves of that posture:
#   (a) The PS script header carries the DEPRECATED marker.
#   (b) The PS script is NOT listed in architecture-status.yaml under
#       architecture_sync_gate.implementation: (a deprecated_implementations:
#       sibling key is allowed).
# Drift would let a stale "30-rule pass surface" be re-presented as a shipped
# architecture-sync gate.
# ---------------------------------------------------------------------------
_r61_fail=0
_r61_ps="gate/check_architecture_sync.ps1"
_r61_status="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r61_ps" ]]; then
  fail_rule "legacy_powershell_gate_deprecated" "$_r61_ps missing -- v2.0.0-rc2 deprecation stub expected"
  _r61_fail=1
else
  if ! grep -qE '^\s*Write-Host\s+"DEPRECATED:' "$_r61_ps"; then
    fail_rule "legacy_powershell_gate_deprecated" "$_r61_ps missing DEPRECATED Write-Host banner -- v2.0.0-rc2 second-pass review P0-1"
    _r61_fail=1
  fi
fi
if [[ ! -f "$_r61_status" ]]; then
  fail_rule "legacy_powershell_gate_deprecated" "$_r61_status missing"
  _r61_fail=1
else
  # Extract the architecture_sync_gate.implementation: block and verify the PS
  # path is NOT inside it. The deprecated_implementations: sibling is OK.
  # Capability keys live at 2-space indent (under `capabilities:`); sub-fields
  # at 4-space indent. Exit-capability pattern must match the 2-space level
  # specifically -- a 4-space pattern would never fire and in_cap would leak
  # into every following capability's implementation: block.
  _r61_in_impl=$(awk '
    /^  architecture_sync_gate:[[:space:]]*$/ { in_cap=1; next }
    in_cap && /^  [a-z_]+:/ { in_cap=0; in_impl=0; next }
    in_cap && /^    implementation:[[:space:]]*$/ { in_impl=1; next }
    in_cap && in_impl && /^    [a-z_]+:/ { in_impl=0 }
    in_cap && in_impl && /^[[:space:]]+-[[:space:]]+gate\/check_architecture_sync\.ps1([[:space:]]|$)/ { print "found"; exit }
  ' "$_r61_status")
  if [[ -n "$_r61_in_impl" ]]; then
    fail_rule "legacy_powershell_gate_deprecated" "$_r61_status lists $_r61_ps under architecture_sync_gate.implementation: -- v2.0.0-rc2 requires it under deprecated_implementations: only"
    _r61_fail=1
  fi
fi
if [[ $_r61_fail -eq 0 ]]; then pass_rule "legacy_powershell_gate_deprecated"; fi

# ---------------------------------------------------------------------------
