#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 1 — status_enum_invalid. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 1 — status_enum_invalid
# docs/governance/architecture-status.yaml status: values must be in the
# allowed enum. Any other value is a FAIL.
# ---------------------------------------------------------------------------
_status_path="docs/governance/architecture-status.yaml"
_r1_fail=0
if [[ -f "$_status_path" ]]; then
  # PR-E3.b speedup: single awk pass instead of 1388 sed subprocesses.
  # awk emits the first invalid status value it finds (or empty for clean).
  _r1_bad=$(awk '
    BEGIN {
      ok["design_accepted"]=1; ok["implemented_unverified"]=1
      ok["test_verified"]=1; ok["deferred_w1"]=1; ok["deferred_w2"]=1
      # 2026-05-23: "open" added for strategic_decisions: block (founder-level
      # decisions deferred to W3+; tracked outside the architecture phase contract).
      # 2026-05-23: "design_only" added for contract-status rows whose
      # spec exists but runtime enforcement is deferred (Rule 62 vocab).
      ok["open"]=1; ok["design_only"]=1
    }
    /^[[:space:]]*status:[[:space:]]*[A-Za-z_]+[[:space:]]*$/ {
      val = $0
      sub(/^[[:space:]]*status:[[:space:]]*/, "", val)
      sub(/[[:space:]]+$/, "", val)
      if (!(val in ok)) { print val; exit }
    }
  ' "$_status_path")
  if [[ -n "$_r1_bad" ]]; then
    fail_rule "status_enum_invalid" "status '$_r1_bad' not in allowed enum {design_accepted,implemented_unverified,test_verified,deferred_w1,deferred_w2} in $_status_path"
    _r1_fail=1
  fi
  if [[ $_r1_fail -eq 0 ]]; then pass_rule "status_enum_invalid"; fi
else
  fail_rule "status_enum_invalid" "$_status_path not found"
fi

# ---------------------------------------------------------------------------
