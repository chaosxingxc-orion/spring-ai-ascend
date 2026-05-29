#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 21 — bom_glue_paths_exist. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 21 — bom_glue_paths_exist
# ADR-0043: docs/cross-cutting/oss-bill-of-materials.md must not contain the
# known ghost implementation paths unless the path exists on disk.
# ---------------------------------------------------------------------------
_r21_fail=0
_bom21='docs/cross-cutting/oss-bill-of-materials.md'
_ghost_paths21=(
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/llm/ChatClientFactory'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/llm/LlmRouter'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/PgVectorAdapter'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/temporal/RunWorkflow'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/tool/McpToolRegistry'
)
if [[ -f "$_bom21" ]]; then
  for _gp21 in "${_ghost_paths21[@]}"; do
    if grep -qF "$_gp21" "$_bom21" 2>/dev/null; then
      if [[ ! -e "$_gp21" ]]; then
        fail_rule "bom_glue_paths_exist" "$_bom21 references path '$_gp21' which does not exist on disk. Per ADR-0043 Gate Rule 21 BoM glue paths must exist or be removed."
        _r21_fail=1
      fi
    fi
  done
fi
if [[ $_r21_fail -eq 0 ]]; then pass_rule "bom_glue_paths_exist"; fi

# ---------------------------------------------------------------------------
