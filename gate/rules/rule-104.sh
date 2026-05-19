#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 104 — openapi_implemented_route_catalog_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 104 — openapi_implemented_route_catalog_truth (enforcer E146)
#
# Closes rc11 review P2-1 (K-ζ family): catalog (http-api-contracts.md +
# contract-catalog.md) marked POST /v1/runs, GET /v1/runs/{id},
# POST /v1/runs/{id}/cancel as `planned;W1` while the OpenAPI spec and
# RunController.java actually ship the routes. Rule 104 cross-checks live
# Controller @-Mappings against catalog stability markers.
# ---------------------------------------------------------------------------
_r104_fail=0
_r104_catalog="docs/contracts/http-api-contracts.md"
_r104_brief="docs/contracts/contract-catalog.md"
_r104_controller_dir="agent-service/src/main/java"
# Cross-check: for each known live route, the catalog row MUST NOT carry `planned`.
_r104_routes=(
  "POST /v1/runs"
  "GET /v1/runs/{id}"
  "POST /v1/runs/{id}/cancel"
)
for _r104_route in "${_r104_routes[@]}"; do
  _r104_path="${_r104_route##* }"
  _r104_method="${_r104_route%% *}"
  # Live presence: any controller file referencing this path-method combo
  _r104_live=0
  if find "$_r104_controller_dir" -type f -name '*.java' 2>/dev/null \
      | xargs grep -lE "(@${_r104_method^}Mapping|@RequestMapping)[^)]*\"[^\"]*${_r104_path//\//\\/}" 2>/dev/null \
      | head -1 | grep -q .; then
    _r104_live=1
  fi
  [[ $_r104_live -eq 0 ]] && continue
  # Live route — catalog row MUST NOT say "planned"
  for _r104_f in "$_r104_catalog" "$_r104_brief"; do
    [[ -f "$_r104_f" ]] || continue
    if grep -qE "${_r104_route}.*\\(planned" "$_r104_f" 2>/dev/null; then
      fail_rule "openapi_implemented_route_catalog_truth" "$_r104_f marks live shipped route '${_r104_route}' as '(planned...)' -- Rule 104 / E146 (rc11 review P2-1 K-ζ closure; live Controller @-Mapping exists)"
      _r104_fail=1
    fi
  done
done
if [[ $_r104_fail -eq 0 ]]; then pass_rule "openapi_implemented_route_catalog_truth"; fi

