#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 96 — kernel_deferred_clause_coherence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 96 — kernel_deferred_clause_coherence (enforcer E133)
#
# Closes rc8 post-corrective review P1-1: active rule kernels (Rule 42, Rule 46)
# stated runtime obligations that `docs/CLAUDE-deferred.md` correctly defers
# to W2. Rule 96 asserts that for every `Rule N.<letter>` block in
# CLAUDE-deferred.md, the matching `#### Rule N` kernel block in CLAUDE.md
# acknowledges the sub-clause by literal-string reference (e.g. `Rule 42.b`).
# ---------------------------------------------------------------------------
_r96_fail=0
_r96_claude="CLAUDE.md"
_r96_deferred="docs/CLAUDE-deferred.md"
if [[ ! -f "$_r96_claude" ]]; then
  fail_rule "kernel_deferred_clause_coherence" "$_r96_claude missing -- Rule 96 / E133"
  _r96_fail=1
elif [[ ! -f "$_r96_deferred" ]]; then
  # CLAUDE-deferred.md eliminated per user directive 2026-05-28 (authority-drift source).
  # Rule has no subject matter when the deferred file is absent; pass vacuously.
  :
else
  _r96_missing=""
  _r96_seen=0
  # Active #### Rule ids in CLAUDE.md (namespaced D-/R-/G-/M- or legacy numeric),
  # used for longest-prefix parent resolution of a deferred sub-clause heading.
  _r96_ids=$(grep -oE '^#### Rule [A-Za-z0-9.-]+' "$_r96_claude" | sed -E 's/^#### Rule //')
  # Every deferred-clause heading in CLAUDE-deferred.md. Post-rc16 these are
  # namespaced ("## Rule R-K.c", "## Rule R-M sub-clause .d.c") not numeric, so the
  # heading id is the text after "## Rule " up to the " — " title separator. A bare
  # id with no sub-clause names a fully-deferred rule with no active kernel block.
  while IFS= read -r _r96_head; do
    [[ -z "$_r96_head" ]] && continue
    _r96_raw="${_r96_head%% — *}"
    _r96_raw="$(printf '%s' "$_r96_raw" | sed -E 's/[[:space:]]+$//')"
    # Normalise "X sub-clause .a.b" -> "X.a.b" for parent resolution only.
    _r96_norm="$(printf '%s' "$_r96_raw" | sed -E 's/ sub-clause \././g')"
    case "$_r96_norm" in *.*) ;; *) continue ;; esac
    # Longest dotted prefix of the normalised id that is an active #### Rule block.
    _r96_parent=""
    _r96_try="$_r96_norm"
    while [[ "$_r96_try" == *.* ]]; do
      _r96_try="${_r96_try%.*}"
      if printf '%s\n' "$_r96_ids" | grep -qxF "$_r96_try"; then _r96_parent="$_r96_try"; break; fi
    done
    [[ -z "$_r96_parent" ]] && continue  # parent rule itself deferred (no active kernel)
    _r96_seen=$((_r96_seen + 1))
    _r96_ref="Rule ${_r96_raw}"
    # Extract the `#### Rule <parent>` block via literal prefix match (parent ids
    # contain '.' so a regex anchor would over-match; index/substr stays literal).
    _r96_hdr="#### Rule ${_r96_parent}"
    _r96_block=$(awk -v hdr="$_r96_hdr" '
      index($0, hdr) == 1 && (substr($0, length(hdr)+1, 1) == " " || $0 == hdr) { in_block = 1; print; next }
      in_block && /^---$/ { exit }
      in_block { print }
    ' "$_r96_claude")
    # Coherence is satisfied if EITHER the CLAUDE.md kernel OR the matching rule card
    # references the sub-clause by literal name. Rule cards have no kernel_cap.
    _r96_card="docs/governance/rules/rule-${_r96_parent}.md"
    _r96_kernel_has=0
    _r96_card_has=0
    printf '%s' "$_r96_block" | grep -qF "$_r96_ref" && _r96_kernel_has=1
    [[ -f "$_r96_card" ]] && grep -qF "$_r96_ref" "$_r96_card" && _r96_card_has=1
    if [[ $_r96_kernel_has -eq 0 ]] && [[ $_r96_card_has -eq 0 ]]; then
      _r96_missing="${_r96_missing}[${_r96_ref}] "
    fi
  done < <(grep -E '^## Rule ' "$_r96_deferred" | sed -E 's/^## Rule //')
  # Non-vacuity guard relaxed 2026-05-28: with CLAUDE.md now the collaboration-only
  # kernel, parent rules (R-K, R-M, etc.) of deferred sub-clauses live in rule
  # cards, not in CLAUDE.md headings. Resolving zero parents via CLAUDE.md headings
  # is the new normal, not drift. Rule's coherence check (kernel+card OR card alone
  # references the sub-clause name) is now subsumed by the deferred_sub_clauses:
  # YAML field migration into rule cards (Phase 7 step 7.2).
  if [[ $_r96_seen -eq 0 ]]; then
    : # vacuous OK -- sub-clauses now live in rule card frontmatter, not deferred file
  fi
  if [[ -n "$_r96_missing" ]]; then
    fail_rule "kernel_deferred_clause_coherence" "Active rule kernel + rule card pair does not acknowledge deferred sub-clause(s): ${_r96_missing}-- Rule 96 / E133 (add explicit 'Rule N.X' literal-string reference in either CLAUDE.md kernel block OR docs/governance/rules/rule-NN.md card; rc8 post-corrective P1-1 closure)"
    _r96_fail=1
  fi
fi
if [[ $_r96_fail -eq 0 ]]; then pass_rule "kernel_deferred_clause_coherence"; fi

# ---------------------------------------------------------------------------
