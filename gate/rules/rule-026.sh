#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 26 — release_note_shipped_surface_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 26 — release_note_shipped_surface_truth
# ADR-0046: docs/logs/releases/*.md must not overclaim shipped surfaces.
#   26a — RunLifecycle name guard: line containing 'RunLifecycle' must be in a one-line
#         context window with a wave qualifier W1/W2/W3/W4, OR the same line must contain
#         one of: design-only|deferred|not shipped|remains design|materialised at W.
#   26b — RunContext method-list guard: line listing RunContext methods MUST NOT contain
#         posture() and method tokens must be subset of {runId,tenantId,checkpointer,suspendForChild}.
#   26c — OpenAPI snapshot attribution: ApiCompatibilityTest co-mentioned with
#         snapshot|OpenAPI.*spec|diverges fails (unless ArchUnit-only disclaimer present).
#   26d — AppPostureGate scope guard: 'AppPostureGate' on a line with 'HTTP Edge' fails;
#         'all runtime components.*posture.*constructor' fails.
# Closes GATE-SCOPE-GAP for release artifact class.
# ---------------------------------------------------------------------------
_r26_fail=0
if [[ -d docs/logs/releases ]]; then
  while IFS= read -r _rf26; do
    [[ -z "$_rf26" ]] && continue
    # rc32 perf: frozen release notes are immutable historical artefacts;
    # re-validating them on every gate run is wasted work (Rule 28 already
    # exempts them; Rule 26 should too). This dropped Rule 26 wall-clock
    # from 300s timeout to under 30s on a corpus with 25+ release notes.
    if grep -q "Historical artifact frozen at SHA" "$_rf26"; then
      continue
    fi
    # Pre-read file into an array of lines for context-window 26a.
    mapfile -t _rf26_lines < "$_rf26"
    _rf26_count=${#_rf26_lines[@]}
    for ((_i26=0; _i26 < _rf26_count; _i26++)); do
      _ln26="${_rf26_lines[$_i26]}"
      _lno26=$((_i26 + 1))
      # Narrative exemption: lines that explicitly describe Rule 26 itself are meta,
      # not shipped-surface claims. Skip them.
      if printf '%s' "$_ln26" | grep -qE 'Gate Rule 26|ADR-0046|release_note_shipped_surface_truth'; then
        continue
      fi
      # 26a: RunLifecycle name guard
      if printf '%s' "$_ln26" | grep -q 'RunLifecycle'; then
        _lo26=$((_i26 > 0 ? _i26 - 1 : 0))
        _hi26=$((_i26 + 1 < _rf26_count ? _i26 + 1 : _i26))
        _ctx26a=""
        for ((_j26=_lo26; _j26 <= _hi26; _j26++)); do
          _ctx26a="$_ctx26a ${_rf26_lines[$_j26]}"
        done
        _has_wave26a=0
        if printf '%s' "$_ctx26a" | grep -qE '(^|[^A-Za-z0-9])W[1-4]([^A-Za-z0-9]|$)'; then _has_wave26a=1; fi
        _has_marker26a=0
        if printf '%s' "$_ln26" | grep -qE 'design-only|deferred|not shipped|remains design|materialised at W|materialized at W'; then _has_marker26a=1; fi
        if [[ $_has_wave26a -eq 0 && $_has_marker26a -eq 0 ]]; then
          fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26a) contains 'RunLifecycle' without W1-W4 wave qualifier in context window or design-only/deferred/not shipped/remains design marker on the same line. Per ADR-0046."
          _r26_fail=1
        fi
      fi
      # 26b: RunContext method-list guard — only fires on methods-context lines
      # (table cell header, methods verb, or RunContext.method( syntax) and extracts
      # tokens only from the substring AFTER the first 'RunContext' occurrence.
      if printf '%s' "$_ln26" | grep -q 'RunContext'; then
        _is_methods_ctx26b=0
        if printf '%s' "$_ln26" | grep -qE '\|[[:space:]]*`?RunContext`?[[:space:]]*\|'; then _is_methods_ctx26b=1; fi
        if printf '%s' "$_ln26" | grep -qE 'RunContext[^.]{0,40}(exposes|interface|methods?|provides|carries|has)'; then _is_methods_ctx26b=1; fi
        if printf '%s' "$_ln26" | grep -qE 'RunContext\.[A-Za-z_]'; then _is_methods_ctx26b=1; fi
        if [[ $_is_methods_ctx26b -eq 1 ]]; then
          # Substring after first RunContext occurrence (POSIX awk).
          _after_rc26=$(printf '%s' "$_ln26" | awk '{ idx = index($0, "RunContext"); if (idx > 0) print substr($0, idx); }')
          if printf '%s' "$_after_rc26" | grep -qE '\bposture[[:space:]]*\('; then
            fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26b) contains 'RunContext' co-mentioned with 'posture()'. Per ADR-0046 RunContext has no posture(); canonical methods are runId/tenantId/checkpointer/suspendForChild."
            _r26_fail=1
          fi
          for _mt26 in $(printf '%s' "$_after_rc26" | grep -oE '\b[A-Za-z_][A-Za-z0-9_]*\(' | sed 's/($//'); do
            case "$_mt26" in
              [a-z]*)
                case "$_mt26" in
                  runId|tenantId|checkpointer|suspendForChild) : ;;
                  exposes|lists|returns|threads|carries|provides|sourced|interface|method|methods|requires|reads|writes|sees|gets|fails) : ;;
                  *)
                    fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26b) lists method '$_mt26()' alongside 'RunContext' in a methods-context. Per ADR-0046 canonical RunContext methods are {runId, tenantId, checkpointer, suspendForChild}; other tokens flag an invented method."
                    _r26_fail=1
                    ;;
                esac
                ;;
              *) : ;;
            esac
          done
        fi
      fi
      # 26c: OpenAPI snapshot test attribution
      if printf '%s' "$_ln26" | grep -q 'ApiCompatibilityTest' && \
         printf '%s' "$_ln26" | grep -qE 'snapshot|OpenAPI[[:space:]]*(snapshot|spec|v1)|diverges|live[[:space:]]*spec'; then
        if ! printf '%s' "$_ln26" | grep -qE 'ArchUnit[[:space:]]*-?[[:space:]]*only|not[[:space:]]+the[[:space:]]+OpenAPI|is[[:space:]]+not[[:space:]]+the[[:space:]]+OpenAPI'; then
          fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26c) attributes OpenAPI snapshot enforcement to ApiCompatibilityTest. Per ADR-0046 the snapshot diff lives in OpenApiContractIT (via OpenApiSnapshotComparator). ApiCompatibilityTest is ArchUnit-only."
          _r26_fail=1
        fi
      fi
      # 26d: AppPostureGate scope guard
      if printf '%s' "$_ln26" | grep -q 'AppPostureGate' && printf '%s' "$_ln26" | grep -qE 'HTTP[[:space:]]*Edge'; then
        fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26d) co-mentions 'AppPostureGate' with 'HTTP Edge'. Per ADR-0046 AppPostureGate lives in agent-runtime; it does not belong under HTTP Edge."
        _r26_fail=1
      fi
      if printf '%s' "$_ln26" | grep -qE 'all[[:space:]]+runtime[[:space:]]+components.*posture.*constructor|posture.*constructor.*all[[:space:]]+runtime[[:space:]]+components'; then
        fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26d) claims posture is a constructor argument for all runtime components. Per ADR-0046 only SyncOrchestrator, InMemoryRunRegistry, InMemoryCheckpointer call AppPostureGate; the claim is over-generalised."
        _r26_fail=1
      fi
    done
  done < <(find docs/logs/releases -name '*.md' -type f 2>/dev/null | sort || true)
fi
if [[ $_r26_fail -eq 0 ]]; then pass_rule "release_note_shipped_surface_truth"; fi

# ---------------------------------------------------------------------------
