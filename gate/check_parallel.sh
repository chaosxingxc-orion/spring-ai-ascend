#!/usr/bin/env bash
# gate/check_parallel.sh — parallel wrapper around gate/check_architecture_sync.sh
#
# Background: the canonical gate (check_architecture_sync.sh) runs 63+
# stateless rules sequentially in one bash process. On Windows / git-bash
# that takes ~20+ minutes because every grep/find/awk/sed spawns a Win32
# process (slow under MSYS).
#
# This wrapper:
#   1. Reads the canonical script and splits it on `# Rule N — <slug>` markers.
#   2. For each rule, extracts the body lines.
#   3. Round-robin distributes rules into JOBS batches.
#   4. Each batch is a bash script that sources the shared prologue ONCE,
#      then runs each rule body in a subshell that resets `fail_count`.
#   5. Batches run in parallel via xargs -P.
#   6. Outputs PASS/FAIL lines in deterministic rule-number order and
#      returns 0 if every rule's fail_count==0.
#
# Opt-out:  GATE_PARALLEL=0 bash gate/check_parallel.sh
#           (falls through to the canonical serial script).
#
# Profiling: GATE_PROFILE=1 bash gate/check_parallel.sh
#           (emits per-rule wall-clock to stderr at end).
#
# Per CLAUDE.md Rule 3 + Rule 28: this wrapper produces identical PASS/FAIL
# semantics (and deterministic ordering) to the canonical script, so CI,
# self-tests, and humans observe no behavioural difference.

set -uo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# ---------------------------------------------------------------------------
# PR-E1 config loader -- populates GATE_PARALLELISM_JOBS, GATE_LOGGING_*, etc.
# PR-E2 wave consumes the logging knobs to enable NDJSON / summary / retention.
# Both are no-ops if the files are absent (graceful fallback to hardcoded defaults).
# ---------------------------------------------------------------------------
if [[ -f "$repo_root/gate/lib/load_config.sh" ]]; then
  GATE_REPO_ROOT="$repo_root"
  export GATE_REPO_ROOT
  # shellcheck source=gate/lib/load_config.sh
  source "$repo_root/gate/lib/load_config.sh"
  gate_load_config 2>/dev/null || true
fi

SOURCE_SCRIPT="gate/check_architecture_sync.sh"
# Env var GATE_JOBS still wins; falls back to GATE_PARALLELISM_JOBS (config); finally 8.
JOBS="${GATE_JOBS:-${GATE_PARALLELISM_JOBS:-8}}"
PROFILE="${GATE_PROFILE:-${GATE_LOGGING_PROFILE_MODE:-0}}"
# PR-Opt-rc22: per-rule timeout (seconds). Hung rule -> killed + marked FAIL.
# Goal: keep total gate < 5min by killing any individual rule > RULE_TIMEOUT.
# config.yaml#parallelism.rule_timeout_seconds (default 60) sets the value;
# env GATE_RULE_TIMEOUT overrides. -1 disables (back-compat).
RULE_TIMEOUT="${GATE_RULE_TIMEOUT:-${GATE_PARALLELISM_RULE_TIMEOUT_SECONDS:-60}}"
# Total-gate timeout safety net (seconds). Default 300s = 5min ceiling.
TOTAL_TIMEOUT="${GATE_TOTAL_TIMEOUT:-${GATE_PARALLELISM_TOTAL_TIMEOUT_SECONDS:-300}}"
[[ "$PROFILE" == "true" ]] && PROFILE=1
[[ "$PROFILE" == "false" ]] && PROFILE=0

if [[ "${GATE_PARALLEL:-1}" == "0" ]] || [[ "${GATE_PARALLELISM_ENABLED:-true}" == "false" ]]; then
  exec bash "$SOURCE_SCRIPT"
fi

# ---------------------------------------------------------------------------
# PR-E2 NDJSON logging setup: per-run log directory under gate/log/runs/.
# Each gate run produces:
#   gate/log/runs/<sha>_<ts>/per-rule.ndjson   (one JSON object per rule)
#   gate/log/runs/<sha>_<ts>/summary.json      (aggregated stats)
#   gate/log/runs/<sha>_<ts>/manifest.txt      (run-scoped metadata)
# A gate/log/latest symlink (or fallback latest.txt file on systems without
# symlink support) points at the newest run. Honors GATE_LOGGING_NDJSON_ENABLED.
# ---------------------------------------------------------------------------
GATE_NDJSON_ENABLED="${GATE_LOGGING_NDJSON_ENABLED:-true}"
GATE_SUMMARY_ENABLED="${GATE_LOGGING_SUMMARY_ENABLED:-true}"
GATE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || printf '1970-01-01T00:00:00Z')"
GATE_GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo nogit)"
GATE_UNIX_TS="$(date +%s 2>/dev/null || echo 0)"
GATE_RUN_ID="${GATE_GIT_SHA}_${GATE_UNIX_TS}"

if [[ "$GATE_NDJSON_ENABLED" == "true" ]]; then
  GATE_LOG_DIR="${repo_root}/gate/log/runs/${GATE_RUN_ID}"
  mkdir -p "$GATE_LOG_DIR"
  : > "$GATE_LOG_DIR/per-rule.ndjson"
  # Write the run manifest -- aggregate_summary.sh reads this back.
  {
    echo "# gate run manifest (PR-E2 / NDJSON wave)"
    echo "run_id=${GATE_RUN_ID}"
    echo "git_sha=${GATE_GIT_SHA}"
    echo "started_at=${GATE_STARTED_AT}"
    echo "hostname=$(hostname 2>/dev/null || echo unknown)"
    echo "platform=$(uname -s 2>/dev/null || echo unknown)"
    echo "jobs=${JOBS}"
    echo "parallel=true"
    echo "source_script=${SOURCE_SCRIPT}"
  } > "$GATE_LOG_DIR/manifest.txt"
  export GATE_LOG_DIR
else
  GATE_LOG_DIR=""
fi

if [[ ! -f "$SOURCE_SCRIPT" ]]; then
  echo "FAIL: parallel_wrapper -- $SOURCE_SCRIPT not found" >&2
  echo "GATE: FAIL"
  exit 1
fi

WORK_DIR="$(mktemp -d -t gate-parallel.XXXXXX)"
# Perf-investigation hook: keep ms_*.txt under /tmp/gate-last-timings for
# off-band inspection. Cheap (cp 132 small files) and zero impact on PASS/FAIL.
_save_timings() {
  if [[ -d "$WORK_DIR" ]]; then
    mkdir -p /tmp/gate-last-timings 2>/dev/null
    rm -f /tmp/gate-last-timings/ms_*.txt 2>/dev/null
    cp "$WORK_DIR"/ms_*.txt /tmp/gate-last-timings/ 2>/dev/null || true
  fi
  rm -rf "$WORK_DIR"
}
trap '_save_timings' EXIT

# ---------------------------------------------------------------------------
# Extract rule ranges from the source script.
# Boundary: `# Rule N — slug` header (em-dash; double-dash `--` also accepted
# as defence-in-depth for legacy headers — Rule 88 enforces em-dash going
# forward, but the awk stays tolerant so a future drift on separator alone
# does not silently skip rules). End of rules: explicit `# === END OF RULES ===`
# marker, NOT `^# Summary$` (the rc7-post-corrective wave removed the
# `# Summary` documentation-header collision per Rule 88 / E121).
# TSV: rule_order_index<TAB>rule_slug<TAB>start_line<TAB>end_line
# ---------------------------------------------------------------------------
awk '
  function emit_prev(end) {
    if (prev_slug != "") {
      idx = idx + 1
      printf "%03d\t%s\t%d\t%d\n", idx, prev_slug, prev_start, end
    }
  }
  BEGIN { prev_slug = ""; prev_start = 0; idx = 0 }
  /^# Rule [0-9]+.?[a-z]? (—|--) / {
    emit_prev(NR - 1)
    match($0, /^# Rule ([0-9]+.?[a-z]?) (—|--) ([a-z0-9_]+)/, arr)
    prev_slug = arr[1] "_" arr[3]
    prev_start = NR
    next
  }
  /^# === END OF RULES ===$/ {
    emit_prev(NR - 1)
    prev_slug = ""
    exit
  }
  END { emit_prev(NR) }
' "$SOURCE_SCRIPT" > "$WORK_DIR/manifest.tsv"

total_rules=$(wc -l < "$WORK_DIR/manifest.tsv")
if [[ "$total_rules" -lt "$JOBS" ]]; then JOBS="$total_rules"; fi

# ---------------------------------------------------------------------------
# Build a shared prologue: everything before the first rule body, with the
# repo_root computation overridden (canonical computes it from $BASH_SOURCE,
# which would resolve to $WORK_DIR for our subscripts).
# ---------------------------------------------------------------------------
first_rule_line=$(head -1 "$WORK_DIR/manifest.tsv" | cut -f3)
sed -n "1,$((first_rule_line - 1))p" "$SOURCE_SCRIPT" \
  | sed -E "s|^repo_root=\"\\\$\\(cd .*\\)\"$|repo_root=\"$repo_root\"|" \
  > "$WORK_DIR/prologue.sh"

# Cross-rule shared constants (rule 28f defines _efile, rules 28i/28j read it;
# similar for a few others). Pre-define them in the prologue so each isolated
# rule body finds them. Safe to redefine — they are read-only file paths.
cat >> "$WORK_DIR/prologue.sh" <<'SHIM'
# --- gate/check_parallel.sh shared-context shim ---
_efile='docs/governance/enforcers.yaml'
_archfile='ARCHITECTURE.md'
_status_file='docs/governance/architecture-status.yaml'
_status_path='docs/governance/architecture-status.yaml'
_python_bin="$(command -v python3 || command -v python || echo '')"
# --- end shim ---
SHIM

# ---------------------------------------------------------------------------
# Perf fix (2026-05-23): pre-populate scan_cache ONCE and export the result
# file path so per-rule subshells (each spawned via timeout -k 5 60 bash -c)
# short-circuit the 1.6s find/git work. Net saving on WSL is ~211 s.
#
# We source scan_cache.sh in a subshell (so we don't pollute the orchestrator
# environment), capture the _SCAN_* exports, and write them as a sourceable
# env file. Subshells inheriting GATE_SCAN_CACHE_FILE see it and skip work.
# ---------------------------------------------------------------------------
SCAN_CACHE_FILE="$WORK_DIR/scan_cache.env"
# Resolve python early so the scan_cache pre-population subshell honours the
# same python3-then-python fallback the aggregator uses below. Hosts that
# only have `python` on PATH (Windows native, some conda environments) would
# otherwise silently lose the 211s scan-cache optimisation.
GATE_PYTHON_BIN="$(command -v python3 || command -v python || echo '')"
export GATE_PYTHON_BIN
if [[ -f "$repo_root/gate/lib/scan_cache.sh" && -n "$GATE_PYTHON_BIN" ]]; then
  (
    # Source scan_cache; it auto-populates the _SCAN_* vars.
    # shellcheck disable=SC1091
    source "$repo_root/gate/lib/scan_cache.sh"
    # Emit each _SCAN_ var as a here-doc-safe export. Values may contain
    # newlines and special chars (find output), so we use Python for safe
    # shell-quoting.
    "$GATE_PYTHON_BIN" - "$SCAN_CACHE_FILE" <<'PYEOF'
import os, shlex, sys
out = sys.argv[1]
keys = [k for k in os.environ if k.startswith("_SCAN_")]
with open(out, "w", encoding="utf-8") as f:
    f.write("# Auto-generated by gate/check_parallel.sh -- pre-populated scan_cache.\n")
    for k in sorted(keys):
        v = os.environ.get(k, "")
        f.write(f"export {k}={shlex.quote(v)}\n")
PYEOF
  )
  if [[ -f "$SCAN_CACHE_FILE" ]]; then
    export GATE_SCAN_CACHE_FILE="$SCAN_CACHE_FILE"
  fi
fi

# ---------------------------------------------------------------------------
# Extract each rule's body into its own file (just the rule's lines).
# ---------------------------------------------------------------------------
while IFS=$'\t' read -r idx slug start end; do
  sed -n "${start},${end}p" "$SOURCE_SCRIPT" > "$WORK_DIR/body_${idx}_${slug}.sh"
done < "$WORK_DIR/manifest.tsv"

# ---------------------------------------------------------------------------
# Round-robin distribute rules into JOBS batches.
# ---------------------------------------------------------------------------
awk -v jobs="$JOBS" '{ print ((NR - 1) % jobs) "\t" $0 }' \
    "$WORK_DIR/manifest.tsv" > "$WORK_DIR/manifest.batched.tsv"

for b in $(seq 0 $((JOBS - 1))); do
  batch_script="$WORK_DIR/batch_${b}.sh"
  {
    echo "#!/usr/bin/env bash"
    echo "set +e"
    # Source the shared prologue (helpers, paths, shim) ONCE per batch.
    echo "source \"$WORK_DIR/prologue.sh\""
  } > "$batch_script"
  awk -F'\t' -v want="$b" '$1 == want { print $2 "\t" $3 "\t" $4 "\t" $5 }' \
      "$WORK_DIR/manifest.batched.tsv" \
    | while IFS=$'\t' read -r idx slug _ _; do
        rule_id="${idx}_${slug}"
        body_file="$WORK_DIR/body_${rule_id}.sh"
        out_file="$WORK_DIR/out_${rule_id}.txt"
        exit_file="$WORK_DIR/exit_${rule_id}.txt"
        ms_file="$WORK_DIR/ms_${rule_id}.txt"
        pid_file="$WORK_DIR/pid_${rule_id}.txt"
        # PR-Opt-rc22 / rc27 fix: per-rule timeout via `timeout` command (GNU coreutils).
        # rc27 fix (ADV-1 + CORR-1): the original `bash -c '...'` spawned a fresh
        # shell that did NOT inherit shell functions (fail_rule/pass_rule), so
        # every failure was silently dropped (`command not found` -> _rc=0 -> PASS).
        # Fix: re-source the prologue inside the timeout child so fail_rule and
        # pass_rule are defined; also drop `--preserve-status` so GNU timeout
        # actually exits 124 when it fires (preserve-status returns 143/137).
        cat >> "$batch_script" <<RULE
T0_${idx}=\$(date +%s%3N)
if [[ "\${GATE_RULE_TIMEOUT_DISABLED:-0}" == "1" ]] || ! command -v timeout >/dev/null 2>&1; then
  (
    fail_count=0
    source "$body_file"
    exit "\$fail_count"
  ) > "$out_file" 2>&1
  _rc=\$?
else
  timeout -k 5 ${RULE_TIMEOUT} bash -c "
    source \"$WORK_DIR/prologue.sh\"
    fail_count=0
    source \"$body_file\"
    exit \\\$fail_count
  " > "$out_file" 2>&1
  _rc=\$?
  # GNU timeout exits 124 when it kills the process (without --preserve-status).
  if [[ \$_rc -eq 124 ]]; then
    echo "FAIL: rule_timed_out -- exceeded ${RULE_TIMEOUT}s timeout (PR-Opt-rc22 safety net)" >> "$out_file"
    _rc=1
  fi
fi
echo "\$_rc" > "$exit_file"
echo "\$(( \$(date +%s%3N) - T0_${idx} ))" > "$ms_file"
echo "\$\$" > "$pid_file"
RULE
      done
done

# ---------------------------------------------------------------------------
# Run batches in parallel.
# PR-Opt-rc22: total-gate timeout safety net. If the entire gate exceeds
# TOTAL_TIMEOUT, kill the xargs orchestrator and aggregate whatever
# completed. Goal: gate ALWAYS returns under (TOTAL_TIMEOUT + 10s).
# ---------------------------------------------------------------------------
# rc27 fix (CORR-1 + CORR-7): drop --preserve-status (so timeout actually exits 124
# instead of 143/137); also set a fail-close flag so aggregator marks GATE: FAIL
# when total timeout fires.
_total_timeout_fired=0
if [[ "${GATE_TOTAL_TIMEOUT_DISABLED:-0}" == "1" ]] || ! command -v timeout >/dev/null 2>&1; then
  find "$WORK_DIR" -maxdepth 1 -name 'batch_*.sh' -type f -print0 \
    | xargs -0 -n 1 -P "$JOBS" bash
else
  timeout -k 5 "$TOTAL_TIMEOUT" bash -c '
    find "'"$WORK_DIR"'" -maxdepth 1 -name "batch_*.sh" -type f -print0 \
      | xargs -0 -n 1 -P "'"$JOBS"'" bash
  '
  _orchestrator_rc=$?
  if [[ $_orchestrator_rc -eq 124 ]]; then
    echo "FAIL: total_gate_timeout -- aggregator killed after ${TOTAL_TIMEOUT}s (PR-Opt-rc22). Rules not yet completed will be counted as FAIL." >&2
    _total_timeout_fired=1
  fi
fi

# ---------------------------------------------------------------------------
# Aggregate in deterministic rule-number order.
# ---------------------------------------------------------------------------
failed_rules=0
total_subfailures=0
profile_rows=""

# GATE_PYTHON_BIN was resolved earlier (before scan_cache pre-population); it is
# already exported so subshells inherit it. Consumed below by _gate_json_escape
# and the inline NDJSON writer.

# JSON-escape helper for the NDJSON reason field.
_gate_json_escape() {
  if [[ -n "$GATE_PYTHON_BIN" ]]; then
    printf '%s' "$1" | "$GATE_PYTHON_BIN" -c 'import json,sys; sys.stdout.write(json.dumps(sys.stdin.read()))' 2>/dev/null \
      && return 0
  fi
  # Fallback (no python): minimal escape; quote-then-emit.
  printf '"%s"' "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr '\n\t\r' '   ')"
}

while IFS=$'\t' read -r idx slug _ _; do
  rule_id="${idx}_${slug}"
  rule_out="$(cat "$WORK_DIR/out_${rule_id}.txt" 2>/dev/null || true)"
  printf '%s' "$rule_out"
  # Some rule bodies don't append a trailing newline; ensure separation in stdout.
  case "$rule_out" in *$'\n') ;; '') ;; *) echo ;; esac

  rc=0
  [[ -s "$WORK_DIR/exit_${rule_id}.txt" ]] && rc="$(cat "$WORK_DIR/exit_${rule_id}.txt")"
  [[ -z "$rc" ]] && rc=0
  if [[ "$rc" -ne 0 ]]; then
    failed_rules=$((failed_rules + 1))
    total_subfailures=$((total_subfailures + rc))
  fi
  elapsed_ms=0
  [[ -s "$WORK_DIR/ms_${rule_id}.txt" ]] && elapsed_ms="$(cat "$WORK_DIR/ms_${rule_id}.txt")"
  profile_rows="${profile_rows}${elapsed_ms}\t${rule_id}\n"

  # ---- PR-E2: append one NDJSON line per rule to per-rule.ndjson ----------
  if [[ -n "$GATE_LOG_DIR" ]]; then
    # Split the slug field into rule_number + rule_slug. The slug format is
    # "<num>[<a-z>]_<rule_slug>" (e.g. "73_gate_config_well_formed", "28a_...").
    rule_number_part="${slug%%_*}"
    rule_slug_only="${slug#*_}"
    # Numeric component for the rule_number JSON field. Rule IDs come in
    # three shapes: bare "24", lettered "28a", dotted "24.c"; strip from the
    # first non-digit so all three normalise to their digit prefix and the
    # downstream `printf '%d'` does not choke on "24." (the dot survived a
    # naive `%%[a-z]` strip and printf 24. emits "invalid number").
    rule_number_int="${rule_number_part%%[!0-9]*}"
    # Pid the worker batch ran in.
    worker_pid=0
    [[ -s "$WORK_DIR/pid_${rule_id}.txt" ]] && worker_pid="$(cat "$WORK_DIR/pid_${rule_id}.txt")"
    # Status + reason. If rc != 0 and the rule body emitted FAIL: lines, the
    # first FAIL line's reason becomes our reason. Else generic "non-zero exit".
    if [[ "$rc" -eq 0 ]]; then
      status="PASS"
      reason_json="null"
    else
      status="FAIL"
      first_fail_line="$(printf '%s' "$rule_out" | grep -m1 '^FAIL:' || true)"
      if [[ -n "$first_fail_line" ]]; then
        # Strip "FAIL: <slug> -- " prefix
        reason_text="$(printf '%s' "$first_fail_line" | sed -E 's/^FAIL:[[:space:]]+[^-]*--[[:space:]]*//')"
      else
        reason_text="rule body exited non-zero (rc=$rc) without emitting FAIL line"
      fi
      reason_json="$(_gate_json_escape "$reason_text")"
    fi
    finished_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo 1970-01-01T00:00:00Z)"
    # Note: rule_number in JSON is the integer; rule_id keeps the optional letter.
    rule_id_full="${rule_number_part}_${rule_slug_only}"
    printf '{"rule_id":"%s","rule_number":%d,"rule_slug":"%s","status":"%s","duration_ms":%d,"finished_at":"%s","reason":%s,"worker_pid":%d}\n' \
      "$rule_id_full" "$rule_number_int" "$rule_slug_only" "$status" "$elapsed_ms" "$finished_iso" "$reason_json" "$worker_pid" \
      >> "$GATE_LOG_DIR/per-rule.ndjson"
  fi
done < "$WORK_DIR/manifest.tsv"

if [[ "$PROFILE" == "1" ]]; then
  echo "--- gate parallel profile (per-rule wall-clock, ms, slowest first) ---" >&2
  printf '%b' "$profile_rows" | sort -rn | head -20 >&2
fi

# ---------------------------------------------------------------------------
# PR-E2: finalize the run -- sort per-rule.ndjson, write summary.json, prune.
# ---------------------------------------------------------------------------
if [[ -n "$GATE_LOG_DIR" ]]; then
  bash "$repo_root/gate/lib/aggregate_summary.sh" "$GATE_LOG_DIR" || true
  # Refresh the gate/log/latest symlink. ln -sfn is atomic on POSIX. On
  # MSYS / git-bash the env var below enables real symlinks; on plain
  # systems without symlink privileges we fall back to a latest.txt file.
  (
    cd "$repo_root/gate/log" 2>/dev/null || exit 0
    if MSYS=winsymlinks:nativestrict ln -sfn "runs/${GATE_RUN_ID}" latest 2>/dev/null; then
      :
    elif ln -sfn "runs/${GATE_RUN_ID}" latest 2>/dev/null; then
      :
    else
      # Fallback: write a text file pointing at the run dir. Self-tests
      # treat either form as authoritative.
      printf 'runs/%s\n' "$GATE_RUN_ID" > latest.txt
    fi
  )
  bash "$repo_root/gate/lib/prune_old_runs.sh" || true
fi

# rc8: emit a parallel_summary trailer so reviewers and Rule 88 can audit
# coverage without re-deriving counts. The serial-source count is taken from
# the canonical script's rule-header set (em-dash or double-dash separator
# tolerant); the parallel count is the size of manifest.tsv built above.
_serial_rule_count=$(grep -cE '^# Rule [0-9]+.?[a-z]? (—|--) ' "$SOURCE_SCRIPT" 2>/dev/null || echo 0)
echo "parallel_summary: executed ${total_rules} rules; serial source defined ${_serial_rule_count} rules"
if [[ "${total_rules}" != "${_serial_rule_count}" ]]; then
  echo "GATE: FAIL (parallel/serial parity: executed ${total_rules} != serial ${_serial_rule_count}; Rule 88 / E121 would catch this in the serial canonical gate)"
  exit 1
fi

if [[ "$_total_timeout_fired" == "1" ]]; then
  echo "GATE: FAIL (total_gate_timeout fired -- killed after ${TOTAL_TIMEOUT}s; $failed_rules of $total_rules rules failed before kill; $total_subfailures sub-failures total)"
  exit 1
fi
if [[ "$failed_rules" -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL ($failed_rules of $total_rules rules failed; $total_subfailures sub-failures total)"
  exit 1
fi
