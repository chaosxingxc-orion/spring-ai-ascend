#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 45 — bus_channels_three_track_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 45 — bus_channels_three_track_present (enforcer E64, Rule 35 / P-E)
#
# docs/governance/bus-channels.yaml MUST exist; declare 3 channels with ids
# control / data / rhythm; each MUST have a unique physical_channel: value.
# ---------------------------------------------------------------------------
_r45_fail=0
_r45_path="docs/governance/bus-channels.yaml"
if [[ ! -f "$_r45_path" ]]; then
  fail_rule "bus_channels_three_track_present" "$_r45_path missing — Rule 35 / P-E ironclad rule unenforced"
  _r45_fail=1
else
  # Extract id: values under channels:
  _r45_ids="$(awk '/^channels:[[:space:]]*$/{in_ch=1; next} /^[a-zA-Z]/{in_ch=0} in_ch && /^[[:space:]]+- id:/{sub(/^[[:space:]]+- id:[[:space:]]*/,""); sub(/[[:space:]].*$/,""); print}' "$_r45_path")"
  _r45_count="$(printf '%s\n' "$_r45_ids" | grep -c .)"
  if [[ "$_r45_count" -ne 3 ]]; then
    fail_rule "bus_channels_three_track_present" "$_r45_path declares $_r45_count channel ids; expected exactly 3 (control/data/rhythm)"
    _r45_fail=1
  else
    for _expected in control data rhythm; do
      if ! printf '%s\n' "$_r45_ids" | grep -qx "$_expected"; then
        fail_rule "bus_channels_three_track_present" "$_r45_path missing required channel id: $_expected"
        _r45_fail=1
      fi
    done
    # Extract physical_channel: values; must be unique
    _r45_phys="$(grep -E '^[[:space:]]+physical_channel:' "$_r45_path" | sed -E 's/^[[:space:]]+physical_channel:[[:space:]]*//; s/[[:space:]].*$//')"
    _r45_phys_count="$(printf '%s\n' "$_r45_phys" | grep -c .)"
    _r45_phys_uniq="$(printf '%s\n' "$_r45_phys" | sort -u | grep -c .)"
    if [[ "$_r45_phys_count" -ne "$_r45_phys_uniq" ]]; then
      fail_rule "bus_channels_three_track_present" "$_r45_path channels share physical_channel: identifiers (got $_r45_phys_count entries, $_r45_phys_uniq unique) — isolation guarantee violated"
      _r45_fail=1
    fi
  fi
fi
if [[ $_r45_fail -eq 0 ]]; then pass_rule "bus_channels_three_track_present"; fi

# ---------------------------------------------------------------------------
