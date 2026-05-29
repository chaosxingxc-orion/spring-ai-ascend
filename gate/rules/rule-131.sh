#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 131 — fact_layer_integrity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 131 — fact_layer_integrity (enforcer E179, kernel Rule G-15)
#
# Authority: ADR-0154 (Fact-Layer Authority, Wave 1).
#
# Sub-clause .a — architecture/facts/{README.md, schema/fact.schema.yaml,
#   generated/} and architecture/profile/saa-property-authority.yaml MUST
#   exist; the YAML surfaces MUST parse. ADVISORY at W1 (no fail-closed,
#   just logged); BLOCKING from W2.
#
# Sub-clauses .b (provenance fields), .c (byte-identical regen + LLM-
# no-author banner), .d (FunctionPoint hard-evidence fields) activate in
# Waves 2, 4, and 5-6 respectively. The single python driver
# gate/lib/check_fact_layer_integrity.py accepts --enforce a,b,c,d to
# select sub-clauses; today only 'a' is enforced.
# ---------------------------------------------------------------------------
_r131_fail=0
_r131_facts_dir="architecture/facts"

if [[ ! -d "$_r131_facts_dir" ]]; then
  fail_rule "fact_layer_integrity" "$_r131_facts_dir missing -- Rule G-15.a requires the fact directory structure; land it from architecture/facts/README.md scaffolding -- Rule G-15 / E179"
  _r131_fail=1
else
  # Round-4 Wave Alpha (2026-05-28 fourth-correction R3 redesign):
  # the bash gate enforces sub-clauses .a (structural existence), .b
  # (provenance/schema validation), .c.structural (banner present —
  # part of .c that doesn't need compiled classes), and .d (FunctionPoint
  # resolver). Sub-clause .c.bytes (byte-identity to extractor
  # re-emission) moved to Maven Surefire test `FactLayerByteIdentityIT`.
  # The Python checker's --enforce 'c' covers the structural banner
  # check that doesn't need target/classes; the byte-diff lives in
  # Maven where target/classes is guaranteed by lifecycle.
  _r131_out=$(python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d 2>&1)
  _r131_rc=$?
  if [[ $_r131_rc -ne 0 ]]; then
    _r131_first=$(printf '%s' "$_r131_out" | head -1)
    fail_rule "fact_layer_integrity" "${_r131_first:-rc=$_r131_rc} -- Rule G-15.a/b/c.structural/d / E179 (sub-clause .c.bytes is enforced by Maven test FactLayerByteIdentityIT)"
    _r131_fail=1
  fi
fi

# Round-4 Wave Alpha (2026-05-28 fourth-correction R3 redesign): the
# byte-identity-to-extractor-re-emission contract (sub-clause .c.bytes)
# moved out of the bash gate and into a Maven Surefire test
# `FactLayerByteIdentityIT` under tools/architecture-workspace, where
# `target/classes` is guaranteed by Maven's compile-phase ordering.
# The bash gate retains structural / provenance / resolver checks
# (sub-clauses .a + .b + .c.structural + .d) which do not require
# compiled classes. This eliminates the precondition-gymnastics that
# bred three rounds of fail-open mechanisms (`|| true`, advisory-skip,
# env-var-opt-in) — there is no longer a "is target/classes present?"
# branch in the bash Rule 131 to be fail-open under.

[[ $_r131_fail -eq 0 ]] && pass_rule "fact_layer_integrity"

# ---------------------------------------------------------------------------
