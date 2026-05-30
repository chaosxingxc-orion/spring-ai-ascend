#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 146 — frame_card_consistency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 146 — frame_card_consistency (enforcer E196, kernel Rule G-29)
#
# Authority: ADR-0161 (EngineeringFrame as Package-Cluster Anchor + Card-over-DSL).
# One ADVISORY helper that pins the Frame Card to the lowest interpretation tier
# of the ADR-0154 cascade — the card invents NO id and NO relationship and never
# outranks a generated fact:
#   * gate/lib/check_frame_card_consistency.py (E196, slug frame_card_consistency)
#     — for every authored Frame Card (architecture/docs/L1/frames/<frame-id>.md)
#     it checks (1) frontmatter frame_id / owner_module / status / primary_package
#     copy the DSL EngineeringFrame element exactly (the DSL wins); (2) every cited
#     code-symbol/<kebab-fqn>, test/<kebab-fqn>, contract-op/<kebab-op-id> — and any
#     code-symbol/<fqn>#<jvm-method-descriptor> — resolves in
#     architecture/facts/generated/*.json (the method ref against the class fact's
#     public_methods[]); (3) every FunctionPoint (FP-…) the card names is anchored
#     by THIS frame in architecture/features/engineering-frames.dsl, or is declared
#     a participating-frame reference (no invented anchors).
# Runs CHANGED-FILES-BLOCKING here (`--mode changed-files-blocking`): a PR may
# not ADD or WORSEN a card violation in a Frame Card it touches — a CHANGED card
# whose identity drifts from the DSL element, or that cites a non-resolving fact,
# or that invents an anchor, BLOCKS; pre-existing findings on untouched cards stay
# advisory. This is the Phase-2 rung in the ADR-0161 §6 ratchet: advisory ->
# changed-files-blocking (this rung) -> full-blocking (the terminal rung after a
# 14-day soak on a clean corpus + a green ProfileYamlParityTest). The helper
# self-derives the changed-card set from git against --base (same git-deriving
# pattern as check_layer_purity.py / Rule 145 / E194); when git cannot resolve the
# base it falls back to full-corpus validation (a safe superset). Base ref =
# BASE_REF (default origin/main) when resolvable, else HEAD. The frames/ directory
# is greenfield until the pilot card lands; with no authored cards the helper is
# vacuously clean. The instant one card exists, the DSL elements and the generated
# facts MUST be readable or the helper fails closed (exit 2) — a missing authority
# is never an advisory condition, so it is surfaced as a hard fail in every mode.
# A missing helper fails closed; a missing python interpreter is a vacuous pass
# (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: architecture/docs/L1/frames/*.md, architecture/features/engineering-frames.dsl, architecture/features/features.dsl, architecture/features/function-points.dsl, architecture/facts/generated/code-symbols.json, architecture/facts/generated/tests.json, architecture/facts/generated/contract-surfaces.json, gate/lib/check_frame_card_consistency.py
# ---------------------------------------------------------------------------
_r146_fail=0
_r146_helper="gate/lib/check_frame_card_consistency.py"
_r146_base="${BASE_REF:-origin/main}"
if ! { command -v git >/dev/null 2>&1 && git rev-parse --verify "$_r146_base" >/dev/null 2>&1; }; then
  _r146_base="HEAD"
fi
if [[ ! -f "$_r146_helper" ]]; then
  fail_rule "frame_card_consistency" "$_r146_helper missing -- Rule G-29 / E196"
  _r146_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r146_out=$("$GATE_PYTHON_BIN" "$_r146_helper" --mode changed-files-blocking --base "$_r146_base" 2>&1)
  _r146_rc=$?
  # A non-zero rc is EITHER a blocked changed-card finding (exit 1) OR a CONFIG
  # ERROR (a card exists but the DSL/facts authority vanished, exit 2). Both fail
  # the rule; the config-error message is surfaced verbatim when present.
  if [[ $_r146_rc -ne 0 ]]; then
    _r146_err=$(printf '%s' "$_r146_out" | grep -E 'config error' | head -1)
    if [[ -n "$_r146_err" ]]; then
      fail_rule "frame_card_consistency" "${_r146_err} -- Rule G-29 / E196"
    else
      _r146_sum=$(printf '%s' "$_r146_out" | grep -E 'finding\(s\)' | tail -1)
      _r146_hits=$(printf '%s' "$_r146_out" | grep -E '^frame-card [^ ]+ \[' | head -5)
      fail_rule "frame_card_consistency" "changed Frame Card adds a card violation (Rule G-29 / E196): ${_r146_sum:-frame-card helper exited $_r146_rc}${_r146_hits:+ || ${_r146_hits}}"
    fi
    _r146_fail=1
  else
    _r146_sum=$(printf '%s' "$_r146_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r146_sum" ]] && echo "OK (Rule G-29 / E196 changed-files-blocking): $_r146_sum"
  fi
fi
[[ $_r146_fail -eq 0 ]] && pass_rule "frame_card_consistency"

