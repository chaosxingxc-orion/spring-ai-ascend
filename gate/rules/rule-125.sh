#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 125 — codegraph_install_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 125 — codegraph_install_truth (enforcer E173)
#
# Operationalises Rule R-A's developer-self-service clause for the
# project-local codegraph MCP tool under tools/codegraph/. Verifies the
# pinning surfaces a fresh contributor needs to reproduce the install:
#   (a) tools/codegraph/package.json declares @colbymchenry/codegraph at an
#       EXACT pin (X.Y.Z form, no ^/~/>=/<= prefix).
#   (b) tools/codegraph/package-lock.json exists with lockfileVersion >= 3
#       (older formats omit integrity hashes for optionalDependencies, so
#       per-platform bundles can drift silently between contributors).
#   (c) .mcp.json registers an mcpServers.codegraph entry whose args list a
#       relative shim path under
#       tools/codegraph/node_modules/@colbymchenry/codegraph/<file>
#       (cross-platform, no contributor PATH dependency).
# Does NOT require node_modules/ to be materialised; CI without `npm ci`
# still passes. This rule guards the pinning truth, not the install state.
#
# scope_surfaces: tools/codegraph/package.json, tools/codegraph/package-lock.json, .mcp.json
# ---------------------------------------------------------------------------
_r125_fail=0
_r125_pkg="tools/codegraph/package.json"
_r125_lock="tools/codegraph/package-lock.json"
_r125_mcp=".mcp.json"

if [[ ! -f "$_r125_pkg" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_pkg missing -- contributor onboarding broken; restore the pinned manifest under tools/codegraph -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"@colbymchenry/codegraph":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' "$_r125_pkg"; then
  fail_rule "codegraph_install_truth" "$_r125_pkg: @colbymchenry/codegraph must be exact-pinned (X.Y.Z form, no ^/~/>=/<= prefix) -- Rule R-A / E173"
  _r125_fail=1
fi

if [[ ! -f "$_r125_lock" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_lock missing -- run \`cd tools/codegraph && npm install\` to regenerate the lockfile -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"lockfileVersion":[[:space:]]*[3-9][0-9]*' "$_r125_lock"; then
  fail_rule "codegraph_install_truth" "$_r125_lock: lockfileVersion must be >= 3 (older formats omit integrity hashes for optionalDependencies) -- Rule R-A / E173"
  _r125_fail=1
fi

if [[ ! -f "$_r125_mcp" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_mcp missing -- project-scope MCP wiring absent; Claude Code contributors cannot load codegraph without it -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -q '"codegraph"' "$_r125_mcp"; then
  fail_rule "codegraph_install_truth" "$_r125_mcp: no mcpServers.codegraph entry registered -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"tools/codegraph/node_modules/@colbymchenry/codegraph/[^"]+"' "$_r125_mcp"; then
  fail_rule "codegraph_install_truth" "$_r125_mcp: codegraph server args must reference a relative path under tools/codegraph/node_modules/@colbymchenry/codegraph/ (cross-platform, no PATH dependency) -- Rule R-A / E173"
  _r125_fail=1
fi

[[ $_r125_fail -eq 0 ]] && pass_rule "codegraph_install_truth"

