#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 126 — template_render_idempotency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 126 — template_render_idempotency (enforcer E174, kernel Rule G-13)
#
# Operationalises Rule G-13.a (Surface Classification): the
# single-source rendering registry MUST exist and be parseable. Today's
# stub verifies existence and structural keys only; Rule G-13.b
# (byte-identical regen of `templated` + `hybrid` outputs against
# render(load_context())) is a forward contract that activates when
# the render engine + general gate driver ship under
# gate/lib/render_template.py + gate/lib/check_template_render_idempotency.py
# + gate/check_template_render_idempotency.sh.
#
# Today's contract:
#   - docs/governance/templates/surface-classification.yaml MUST exist.
#   - It MUST parse as YAML with schema_version + templates keys present.
#   - The templates list MAY be empty.
#
# Forward contract (activated when the render-engine driver lands):
#   - For each entry where bucket in {templated, hybrid}, the template
#     file MUST exist, the context_schema file MUST exist, and
#     render(template, load_context()) MUST byte-match the on-disk output.
#
# scope_surfaces: docs/governance/templates/surface-classification.yaml,
#                 docs/governance/templates/*.md.j2,
#                 docs/governance/rules/rule-G-13.md
# ---------------------------------------------------------------------------
_r126_fail=0
_r126_registry="docs/governance/templates/surface-classification.yaml"

if [[ ! -f "$_r126_registry" ]]; then
  fail_rule "template_render_idempotency" "$_r126_registry missing -- Rule G-13.a requires the surface-classification registry to exist; land it from rule-G-13.md scaffolding -- Rule G-13 / E174"
  _r126_fail=1
else
  _r126_out=$(python3 gate/lib/check_template_render_idempotency.py 2>&1)
  _r126_rc=$?
  if [[ $_r126_rc -ne 0 ]]; then
    _r126_first=$(printf '%s' "$_r126_out" | head -1)
    fail_rule "template_render_idempotency" "${_r126_first:-rc=$_r126_rc} -- Rule G-13.b / E174"
    _r126_fail=1
  fi
fi

# W1 forward-pointer: when surface-classification.yaml has non-empty
# templates list, this rule will also delegate to
# gate/check_template_render_idempotency.sh for the byte-identical
# check. Today the list is empty (W0); the check is vacuously satisfied.

[[ $_r126_fail -eq 0 ]] && pass_rule "template_render_idempotency"

# ---------------------------------------------------------------------------
