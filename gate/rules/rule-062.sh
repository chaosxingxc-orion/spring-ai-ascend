#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 62 — contract_yaml_declares_status. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 62 — contract_yaml_declares_status (v2.0.0-rc2 / second-pass review F-β structural prevention)
#
# Every domain-contract YAML under docs/contracts/*.v1.yaml AND the three
# previously-status-less governance YAMLs (skill-capacity, sandbox-policies,
# bus-channels) MUST declare a top-level `status:` field with a value in
# {design_only, schema_shipped, runtime_enforced}. This codifies the W2.x
# "post-review status label" convention and prevents the F-β defect family
# (deferred-as-live spec drift) from regrowing.
# ---------------------------------------------------------------------------
_r62_fail=0
_r62_allowed_re='^(design_only|schema_shipped|runtime_enforced)$'
_r62_files=(
  "docs/contracts/engine-envelope.v1.yaml"
  "docs/contracts/engine-hooks.v1.yaml"
  "docs/contracts/s2c-callback.v1.yaml"
  "docs/contracts/plan-projection.v1.yaml"
  "docs/governance/evolution-scope.v1.yaml"
  "docs/governance/skill-capacity.yaml"
  "docs/governance/sandbox-policies.yaml"
  "docs/governance/bus-channels.yaml"
)
for _r62_file in "${_r62_files[@]}"; do
  if [[ ! -f "$_r62_file" ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file missing"
    _r62_fail=1
    continue
  fi
  _r62_status_val=$(awk '
    /^status:[[:space:]]+/ {
      v=$0
      sub(/^status:[[:space:]]+/, "", v)
      sub(/[[:space:]]+#.*$/, "", v)
      sub(/[[:space:]]+$/, "", v)
      print v
      exit
    }
  ' "$_r62_file")
  if [[ -z "$_r62_status_val" ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file missing top-level 'status:' field"
    _r62_fail=1
    continue
  fi
  if ! [[ "$_r62_status_val" =~ $_r62_allowed_re ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file has status: '$_r62_status_val' -- must be one of {design_only, schema_shipped, runtime_enforced}"
    _r62_fail=1
  fi
done
if [[ $_r62_fail -eq 0 ]]; then pass_rule "contract_yaml_declares_status"; fi

# ---------------------------------------------------------------------------
