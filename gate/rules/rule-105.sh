#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 105 — edge_no_direct_compute_link. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 105 — edge_no_direct_compute_link (enforcer E144)
#
# Closes ADR-0089 (Edge-Plane Ingress Gateway Mandate) / Rule R-I sub-clause .b
# at the source-grep level. The bytecode complement (E143
# EdgeToComputeDirectLinkArchTest) catches violations at compile/test time;
# this rule catches them at the corpus level so a stray .java file shows up
# in gate output even before the ArchUnit test runs.
#
# Scope:
#   For every <module>/module-metadata.yaml whose `deployment_plane:` is `edge`,
#   scan that module's src/main/java tree for:
#     (a) `^import ascend\.springai\.(service|engine|middleware)\.` lines, OR
#     (b) `new RestTemplate` or `WebClient\.builder` construction targeting a
#         host that isn't the bus ingress endpoint (heuristic: any bare base-URL
#         literal that doesn't contain `bus` is forbidden at W1).
#
# At W1 agent-client is skeleton (no production java) so this rule is
# vacuous-but-armed. When the W3+ SDK lands, the rule starts gating PRs.
# ---------------------------------------------------------------------------
_r105_fail=0
while IFS= read -r _r105_meta; do
  _r105_module_dir="$(dirname "$_r105_meta")"
  _r105_main_java="$_r105_module_dir/src/main/java"
  [[ -d "$_r105_main_java" ]] || continue
  # (a) forbidden compute_control imports
  _r105_violations=$(grep -rnE '^import ascend\.springai\.(service|engine|middleware)\.' "$_r105_main_java" 2>/dev/null || true)
  if [[ -n "$_r105_violations" ]]; then
    while IFS= read -r _r105_line; do
      fail_rule "edge_no_direct_compute_link" "$_r105_line — edge plane module must not import compute_control plane production class; route via com.huawei.ascend.bus.spi.ingress.IngressGateway per Rule R-I sub-clause .b / ADR-0089"
      _r105_fail=1
    done <<< "$_r105_violations"
  fi
  # (b) RestTemplate / WebClient direct construction
  _r105_rest=$(grep -rnE 'new[[:space:]]+RestTemplate\(|WebClient\.builder\(' "$_r105_main_java" 2>/dev/null || true)
  if [[ -n "$_r105_rest" ]]; then
    while IFS= read -r _r105_line; do
      fail_rule "edge_no_direct_compute_link" "$_r105_line — edge plane module must not construct direct HTTP clients; route via com.huawei.ascend.bus.spi.ingress.IngressGateway per Rule R-I sub-clause .b / ADR-0089"
      _r105_fail=1
    done <<< "$_r105_rest"
  fi
done < <(grep -lE '^deployment_plane:[[:space:]]*edge' */module-metadata.yaml 2>/dev/null)
if [[ $_r105_fail -eq 0 ]]; then pass_rule "edge_no_direct_compute_link"; fi

# ---------------------------------------------------------------------------
