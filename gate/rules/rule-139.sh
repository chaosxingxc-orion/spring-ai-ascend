#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 139 — accepted_adr_frame_map_coherence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 139 — accepted_adr_frame_map_coherence (enforcer E187, kernel Rule G-22)
#
# Authority: ADR-0157 (EngineeringFrame Ontology) + ADR-0158 (transport-agnostic
# EnginePort boundary). Closes external review F1.4: an accepted ADR that
# declares an EngineeringFrame re-home or new frame in its decision text MUST be
# reflected in architecture/features/engineering-frames.dsl, else the frame map
# silently lies about the structural axis.
#
# Targeted assertion keyed off the live ADR-0158 case: the DSL MUST declare
# EF-ENGINE-PORT (owner agent-bus) AND place EF-ORCHESTRATION-SPI (owner
# agent-bus) under a genModule_agent_bus contains edge.
#
# scope_surfaces: docs/adr/0158-*.yaml, architecture/features/engineering-frames.dsl
# ---------------------------------------------------------------------------
_r139_fail=0
_r139_dsl="architecture/features/engineering-frames.dsl"
_r139_adr=$(ls docs/adr/0158-*.yaml 2>/dev/null | head -1)
if [[ -z "$_r139_adr" ]]; then
  : # vacuous pass before ADR-0158 lands as yaml
elif ! grep -qE '^[[:space:]]*status:[[:space:]]*accepted' "$_r139_adr"; then
  : # ADR-0158 not accepted yet — frame-map coherence not yet required
elif [[ ! -f "$_r139_dsl" ]]; then
  fail_rule "accepted_adr_frame_map_coherence" "$_r139_dsl missing but ADR-0158 is accepted and declares the EnginePort frame re-home -- Rule G-22 / E187"
  _r139_fail=1
else
  # (a) EF-ENGINE-PORT present with owner agent-bus.
  _r139_ep_block=$(awk '/"saa\.id"[[:space:]]+"EF-ENGINE-PORT"/{f=1} f{print} f&&/^}/{exit}' "$_r139_dsl")
  if ! grep -qE '"saa\.id"[[:space:]]+"EF-ENGINE-PORT"' "$_r139_dsl"; then
    fail_rule "accepted_adr_frame_map_coherence" "$_r139_dsl missing EF-ENGINE-PORT frame required by accepted ADR-0158 -- Rule G-22 / E187"
    _r139_fail=1
  elif ! printf '%s\n' "$_r139_ep_block" | grep -qE '"saa\.owner"[[:space:]]+"agent-bus"'; then
    fail_rule "accepted_adr_frame_map_coherence" "EF-ENGINE-PORT in $_r139_dsl is not owner=agent-bus as ADR-0158 requires -- Rule G-22 / E187"
    _r139_fail=1
  fi
  # (b) EF-ORCHESTRATION-SPI owner agent-bus AND genModule_agent_bus contains edge.
  _r139_os_block=$(awk '/"saa\.id"[[:space:]]+"EF-ORCHESTRATION-SPI"/{f=1} f{print} f&&/^}/{exit}' "$_r139_dsl")
  if ! grep -qE '"saa\.id"[[:space:]]+"EF-ORCHESTRATION-SPI"' "$_r139_dsl"; then
    fail_rule "accepted_adr_frame_map_coherence" "$_r139_dsl missing EF-ORCHESTRATION-SPI frame required by accepted ADR-0158 -- Rule G-22 / E187"
    _r139_fail=1
  elif ! printf '%s\n' "$_r139_os_block" | grep -qE '"saa\.owner"[[:space:]]+"agent-bus"'; then
    fail_rule "accepted_adr_frame_map_coherence" "EF-ORCHESTRATION-SPI in $_r139_dsl is not owner=agent-bus as ADR-0158 requires -- Rule G-22 / E187"
    _r139_fail=1
  elif ! grep -qE '^genModule_agent_bus[[:space:]]*->[[:space:]]*efOrchestrationSpi' "$_r139_dsl"; then
    fail_rule "accepted_adr_frame_map_coherence" "$_r139_dsl missing the genModule_agent_bus -> efOrchestrationSpi contains edge required by ADR-0158 -- Rule G-22 / E187"
    _r139_fail=1
  fi
fi
[[ $_r139_fail -eq 0 ]] && pass_rule "accepted_adr_frame_map_coherence"

# ---------------------------------------------------------------------------
