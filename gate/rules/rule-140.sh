#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 140 — shipped_frame_anchor_integrity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 140 — shipped_frame_anchor_integrity (enforcer E188, kernel Rule G-23)
#
# Authority: ADR-0157 (EngineeringFrame Ontology) + ADR-0158. Closes external
# review F8.3: every SAA EngineeringFrame with saa.status "shipped" MUST anchor
# >=1 FunctionPoint (an anchors edge in engineering-frames.dsl), else the
# shipped status is a structural lie. Frame elements live in BOTH
# engineering-frames.dsl and features.dsl; the anchors edges live in
# engineering-frames.dsl. ADR-backed exceptions are listed in
# gate/frame-shipped-zero-anchor-allowlist.txt (ships empty).
#
# scope_surfaces: architecture/features/engineering-frames.dsl, architecture/features/features.dsl, gate/frame-shipped-zero-anchor-allowlist.txt, gate/lib/check_frame_shipped_anchors.py
# ---------------------------------------------------------------------------
_r140_fail=0
_r140_helper="gate/lib/check_frame_shipped_anchors.py"
if [[ ! -f "$_r140_helper" ]]; then
  fail_rule "shipped_frame_anchor_integrity" "$_r140_helper missing -- Rule G-23 / E188"
  _r140_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r140_out=$("$GATE_PYTHON_BIN" "$_r140_helper" 2>&1)
  _r140_rc=$?
  if [[ $_r140_rc -ne 0 ]]; then
    _r140_first=$(printf '%s' "$_r140_out" | grep -E '^(MISSING-ANCHOR|MISSING-FILE):' | head -1)
    fail_rule "shipped_frame_anchor_integrity" "shipped EngineeringFrame anchors no FunctionPoint: ${_r140_first:-rc=$_r140_rc} -- Rule G-23 / E188"
    _r140_fail=1
  fi
fi
[[ $_r140_fail -eq 0 ]] && pass_rule "shipped_frame_anchor_integrity"

# ---------------------------------------------------------------------------
