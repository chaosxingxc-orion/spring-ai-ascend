#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 57 — engine_hooks_yaml_present_and_wellformed. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 57 — engine_hooks_yaml_present_and_wellformed (enforcer E78, Rule 45 / P-M, ADR-0073)
#
# docs/contracts/engine-hooks.v1.yaml MUST exist with schema:, hooks: list of
# exactly the 9 canonical hook names, and bidirectionally agree with the
# HookPoint enum constants in agent-service/src/main. Drift in either
# direction breaks Rule 45 (Runtime-Owned Middleware via Engine Hooks).
# ---------------------------------------------------------------------------
_r57_fail=0
_r57_yaml="docs/contracts/engine-hooks.v1.yaml"
# Updated 2026-05-17: HookPoint moved from agent-runtime/orchestration/spi/ to
# agent-middleware/spi/ during the six-module materialization PR (T2.B1).
_r57_enum="agent-middleware/src/main/java/com/huawei/ascend/middleware/spi/HookPoint.java"
if [[ ! -f "$_r57_yaml" ]]; then
  fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_yaml missing -- Rule 45 / P-M hook surface unenforced"
  _r57_fail=1
elif [[ ! -f "$_r57_enum" ]]; then
  fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_enum missing -- cannot cross-check HookPoint enum"
  _r57_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+engine-hooks/v1[[:space:]]*$' "$_r57_yaml"; then
    fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_yaml missing 'schema: engine-hooks/v1' header"
    _r57_fail=1
  fi
  # Extract hook names from yaml (lines under 'hooks:' that look like '  - <name>')
  _r57_yaml_hooks=$(awk '/^hooks:/{f=1;next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/{gsub(/^[[:space:]]+- /,""); print}' "$_r57_yaml" | sort -u)
  # Extract HookPoint enum constants (lines like '    BEFORE_LLM_INVOCATION,' or '    ON_ERROR')
  _r57_enum_consts=$(grep -E '^[[:space:]]+[A-Z_]+[,;]?[[:space:]]*$' "$_r57_enum" | sed -E 's/[[:space:]]+([A-Z_]+)[,;]?[[:space:]]*/\1/' | tr 'A-Z_' 'a-z_' | sort -u)
  for _hook in $_r57_yaml_hooks; do
    if ! echo "$_r57_enum_consts" | grep -qxE "${_hook}"; then
      fail_rule "engine_hooks_yaml_present_and_wellformed" "yaml declares hook=$_hook but no matching HookPoint enum constant"
      _r57_fail=1
    fi
  done
  for _const in $_r57_enum_consts; do
    if ! echo "$_r57_yaml_hooks" | grep -qxE "${_const}"; then
      fail_rule "engine_hooks_yaml_present_and_wellformed" "HookPoint enum has constant $_const with no matching yaml hooks: entry"
      _r57_fail=1
    fi
  done
fi
if [[ $_r57_fail -eq 0 ]]; then pass_rule "engine_hooks_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
