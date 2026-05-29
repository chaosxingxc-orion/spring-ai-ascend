#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 99 — kernel_terminal_verb_vs_shipped_decision_check. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 99 — kernel_terminal_verb_vs_shipped_decision_check (enforcer E139)
#
# Closes rc10 post-corrective review P1-1 (J-α family): Rule 41 active kernel
# said "over-cap callers are SUSPENDED, not rejected" but the shipped Java
# surface (DefaultSkillResilienceContract.resolve) returns a decision envelope
# (SkillResolution.reject(SuspendReason.RateLimited)), not a Run state
# transition. The actual SUSPENDED transition is W2 orchestrator wiring per
# CLAUDE-deferred.md Rule 41.c.
#
# Rule 99 prevents recurrence by scanning every active #### Rule N kernel
# block in CLAUDE.md for end-state verb tokens (`are SUSPENDED`, `is
# SUSPENDED`, `transitions to FAILED`, `consumes capacity`, `is rejected`,
# `is admitted`). For each match, the rule checks whether CLAUDE-deferred.md
# declares a Rule N.<letter> sub-clause that defers the same behaviour.
# If BOTH (end-state verb in active kernel) AND (deferred sub-clause exists)
# → FAIL — the active kernel is overclaiming shipped behaviour.
#
# This is the SEMANTIC layer Rule 96 doesn't cover. Rule 96 checks the
# literal `Rule N.<letter>` REFERENCE exists; Rule 99 checks the VERBS in
# the kernel match what's actually shipped.
# ---------------------------------------------------------------------------
_r99_fail=0
_r99_claude="CLAUDE.md"
_r99_deferred="docs/CLAUDE-deferred.md"
if [[ ! -f "$_r99_claude" ]]; then
  fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "$_r99_claude missing -- Rule 99 / E139"
  _r99_fail=1
elif [[ ! -f "$_r99_deferred" ]]; then
  # CLAUDE-deferred.md eliminated per user directive 2026-05-28.
  # Rule 99 has no subject matter when the deferred file is absent; pass vacuously.
  # The deferred_sub_clauses: YAML field migration into rule cards (Phase 7) means
  # terminal-verb checking is now scoped to per-card validation, not cross-file.
  :
else
  # End-state verbs that imply shipped Run-state transitions:
  _r99_end_verbs='are SUSPENDED|is SUSPENDED|callers are SUSPENDED|transitions to FAILED|transitions to SUSPENDED|consumes the .* capacity|is rejected, not failed|admits the caller'
  _r99_violations=""
  # Per-invocation tempfile (mktemp + trap-style cleanup below) so two
  # concurrent gate runs cannot race on a shared /tmp/_r99_hits.<pid> path.
  _r99_hits_file="$(mktemp -t r99_hits.XXXXXX)"
  # Build the set of parent rule ids (namespaced or legacy) that have >=1 deferred
  # sub-clause. Post-rc16 deferred headings are namespaced; resolve each to its
  # longest-prefix active #### Rule id (mirrors Rule 96).
  _r99_ids=$(grep -oE '^#### Rule [A-Za-z0-9.-]+' "$_r99_claude" | sed -E 's/^#### Rule //')
  _r99_deferred_nums=""
  while IFS= read -r _r99_head; do
    [[ -z "$_r99_head" ]] && continue
    _r99_raw="${_r99_head%% — *}"
    _r99_norm="$(printf '%s' "$_r99_raw" | sed -E 's/ sub-clause \././g; s/[[:space:]]+$//')"
    case "$_r99_norm" in *.*) ;; *) continue ;; esac
    _r99_try="$_r99_norm"
    while [[ "$_r99_try" == *.* ]]; do
      _r99_try="${_r99_try%.*}"
      if printf '%s\n' "$_r99_ids" | grep -qxF "$_r99_try"; then
        _r99_deferred_nums="${_r99_deferred_nums}${_r99_try} "; break
      fi
    done
  done < <(grep -E '^## Rule ' "$_r99_deferred" | sed -E 's/^## Rule //')
  _r99_deferred_nums=$(printf '%s\n' $_r99_deferred_nums | sort -u | tr '\n' ' ')
  # For every #### Rule <id> block whose id has a deferred sub-clause, check the
  # kernel body for end-state verbs implying shipped Run-state transitions. Uses
  # 2-arg match() (POSIX-portable; no gawk-only 3-arg array extension).
  awk -v end_verbs="$_r99_end_verbs" -v defnums="$_r99_deferred_nums" '
    BEGIN { rule = ""; body = "" }
    /^#### Rule / {
      if (rule) emit()
      line = $0; sub(/^#### Rule /, "", line); sub(/[ \t].*$/, "", line)
      rule = line
      body = ""
      next
    }
    /^---$/ && rule { emit(); rule = ""; next }
    rule { body = body $0 " " }
    END { if (rule) emit() }
    function emit(   has_deferred, n, dn, i, v) {
      has_deferred = 0
      n = split(defnums, dn, " ")
      for (i = 1; i <= n; i++) if (dn[i] == rule) has_deferred = 1
      if (!has_deferred) return
      if (body ~ end_verbs) {
        match(body, end_verbs)
        v = substr(body, RSTART, RLENGTH)
        print "Rule " rule ":" v
      }
    }
  ' "$_r99_claude" > "$_r99_hits_file"
  _r99_violations=$(cat "$_r99_hits_file")
  rm -f "$_r99_hits_file"
  # Non-vacuity guard relaxed 2026-05-28: with CLAUDE.md now collaboration-only and
  # sub-clauses migrated to rule card frontmatter, zero deferred-sub-clause parents
  # via CLAUDE.md heading resolution is the new normal. Sub-clauses live in the
  # card's deferred_sub_clauses: YAML block, where the terminal-verb check is now
  # the responsibility of per-card validation (separate rule, future).
  if [[ -z "${_r99_deferred_nums// /}" ]]; then
    : # vacuous OK -- sub-clauses migrated to card frontmatter (Phase 7 step 7.2)
  fi
  if [[ -n "$_r99_violations" ]]; then
    _r99_first=$(echo "$_r99_violations" | head -3 | tr '\n' '|')
    fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "active rule kernel uses end-state verb implying shipped Run-state transition, but matching Rule N.<letter> deferred sub-clause exists (kernel is overclaiming shipped behaviour): ${_r99_first}-- Rule 99 / E139 (rc10 post-corrective P1-1 closure; narrow kernel verb to decision-envelope behaviour OR remove the deferred sub-clause if behaviour has actually shipped)"
    _r99_fail=1
  fi
fi
# rc15 widening (Rule G-3.e scope to module ARCHITECTURE.md — sub-check (b),
# enforcer E151 per ADR-0091): scan architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md for the
# specific over-claim phrasing pattern (`over-cap[acity]? callers are
# SUSPENDED` and close variants). The rc14 M-γ defect surfaced when
# `agent-service/ARCHITECTURE.md:315-317` said "over-cap callers are
# SUSPENDED, not rejected" while shipped code + Rule R-K kernel both say
# the W1 surface returns a SkillResolution.reject(SuspendReason.RateLimited)
# decision envelope (Run suspension transition deferred to R-K.c / W2).
# This sub-check catches that exact defect class in module architecture
# docs without conflating with shipped end-state verbs like
# `transitions to FAILED on engine_mismatch` which IS shipped behavior.
# Admissible if the line carries decision-envelope wording or an explicit
# defer marker.
_r99b_hits=$(grep -rnE '(over-cap|over-capacity)( callers| requests)?[^.]*(are SUSPENDED|is SUSPENDED|transitions to SUSPENDED)' \
             architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md 2>/dev/null \
             | grep -vE '(decision envelope|SkillResolution\.reject|deferred to R-K|deferred to Rule R-K|deferred per Rule R-K|W2 scheduler admission)' || true)
if [[ -n "$_r99b_hits" ]]; then
  _r99b_first=$(echo "$_r99b_hits" | head -3 | tr '\n' '|')
  fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "module ARCHITECTURE.md claims shipped over-capacity SUSPENSION while Rule R-K shipped surface returns a decision envelope (suspension deferred to R-K.c / W2). Either rewrite to decision-envelope wording OR add 'deferred to Rule R-K.c' / 'W2 scheduler admission' marker: ${_r99b_first}-- Rule 99 / E151 (Rule G-3.e module-arch scope widening per ADR-0091)"
  _r99_fail=1
fi
if [[ $_r99_fail -eq 0 ]]; then pass_rule "kernel_terminal_verb_vs_shipped_decision_check"; fi

# ---------------------------------------------------------------------------
