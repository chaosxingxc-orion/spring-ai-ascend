#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 149 — ai_understanding_map. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 149 — ai_understanding_map (enforcer E199, kernel Rule G-32)
#
# Authority: ADR-0157 (EngineeringFrame Ontology — the dual-track value/structure
# axes, the derived Feature --traverses--> EngineeringFrame reconciliation, and
# the claim-agnostic Frame). One ADVISORY helper that asserts the explicit
# dual-track understanding map under architecture/mappings/ (the readable
# projection joining VALUE / STRUCTURE / EVIDENCE per FunctionPoint) keeps its two
# value/structure axes DERIVED, never OWNED, over the merged authoring DSL
# architecture/features/{features,function-points,engineering-frames}.dsl. The map
# is a READABLE-INTERPRETATION layer: it records the JOIN over the axes; it invents
# no id and no relationship and never outranks a surface it reads (cascade:
# generated facts > DSL > Card/prose):
#   * gate/lib/check_ai_understanding_map.py (E199, slug ai_understanding_map) —
#     three checks: DERIVED TRAVERSE (every Feature--traverses-->Frame edge is
#     derivable from a shared FunctionPoint the Frame anchors; a Frame anchoring
#     nothing yet is vacuous; NON-DERIVED-TRAVERSE blocks only for a shipped source
#     Feature, advisory otherwise even under full-blocking), NO OWNERSHIP OF A
#     FRAME (a Feature source of a contains/anchors/owns edge into a Frame ->
#     FEATURE-OWNS-FRAME; a non-genModule_* contains source ->
#     NON-MODULE-CONTAINS-FRAME; a Frame carrying saa.productClaim/saa.requirement
#     -> FRAME-OWNS-VALUE), and WELL-TYPED AXES (anchors goes Frame->FunctionPoint,
#     requires goes Feature->FunctionPoint -> MALFORMED-EDGE). ADR-backed exceptions
#     live in gate/ai-understanding-map-allowlist.txt (ships empty).
# Runs ADVISORY here (`--mode advisory`): findings are reported to the gate log and
# never block at this rung. Ratchet: advisory (this rung) -> changed-files-blocking
# (a PR may not ADD a finding once it touches one of the three authoring DSL files;
# the map is a single shared surface, so a change to any re-scopes it) ->
# full-blocking (the terminal rung once the map is clean; a NON-DERIVED-TRAVERSE
# from a not-yet-shipped Feature stays advisory even there). The map is greenfield-
# vacuous until one of the three map DSL files exists; the instant any exists it
# MUST be readable or the helper fails closed (exit 2) in every mode — a missing
# authority is never an advisory condition. A missing helper fails closed; a
# missing python interpreter is a vacuous pass (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: architecture/mappings/ai-understanding-map.yaml, architecture/mappings/ai-understanding-map.md, architecture/features/features.dsl, architecture/features/function-points.dsl, architecture/features/engineering-frames.dsl, gate/ai-understanding-map-allowlist.txt, gate/lib/check_ai_understanding_map.py
# ---------------------------------------------------------------------------
_r149_fail=0
_r149_helper="gate/lib/check_ai_understanding_map.py"
if [[ ! -f "$_r149_helper" ]]; then
  fail_rule "ai_understanding_map" "$_r149_helper missing -- Rule G-32 / E199"
  _r149_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r149_out=$("$GATE_PYTHON_BIN" "$_r149_helper" --mode advisory 2>&1)
  _r149_rc=$?
  # A non-zero rc in advisory mode is a CONFIG ERROR (a map DSL file exists but is
  # unreadable, exit 2) — never an advisory finding (advisory always exits 0).
  # Surface it verbatim as a hard fail.
  if [[ $_r149_rc -ne 0 ]]; then
    _r149_err=$(printf '%s' "$_r149_out" | grep -E 'config error' | head -1)
    fail_rule "ai_understanding_map" "${_r149_err:-ai-understanding-map helper exited $_r149_rc} -- Rule G-32 / E199"
    _r149_fail=1
  else
    _r149_sum=$(printf '%s' "$_r149_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r149_sum" ]] && echo "OK (Rule G-32 / E199 advisory): $_r149_sum"
  fi
fi
[[ $_r149_fail -eq 0 ]] && pass_rule "ai_understanding_map"

