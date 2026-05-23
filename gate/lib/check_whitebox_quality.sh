#!/usr/bin/env bash

# Whitebox-quality report interpreter for gate Rule 121 (Rule G-12 / E169).
#
# Maven owns execution of SpotBugs, PMD, and Checkstyle through the `quality`
# profile; this helper owns repository semantics: report presence,
# high-confidence SpotBugs blocking, low-dispute Checkstyle blocking, and PMD
# review-trigger summarisation.
#
# Parallelism (Rule G-10 discipline): each module's reports are scanned in its
# own backgrounded worker; the driver joins them with an explicit `wait` and
# then aggregates in stable module order so stdout stays deterministic.

_whitebox_repo_root() {
  printf '%s\n' "${GATE_REPO_ROOT:-$(pwd)}"
}

_whitebox_modules() {
  local root
  root="$(_whitebox_repo_root)"
  awk '/<modules>/,/<\/modules>/' "$root/pom.xml" 2>/dev/null \
    | sed -nE 's/^[[:space:]]*<module>([^<]+)<\/module>[[:space:]]*$/\1/p'
}

_whitebox_java_modules() {
  local module root
  root="$(_whitebox_repo_root)"
  while IFS= read -r module; do
    if find "$root/$module/src/main/java" -type f -name '*.java' ! -name 'package-info.java' -print -quit 2>/dev/null | grep -q .; then
      printf '%s\n' "$module"
    fi
  done < <(_whitebox_modules)
}

# Scan a single module's three reports and print its findings. SpotBugs and
# Checkstyle findings print as FAIL lines; the PMD violation count prints as a
# `__PMD__<TAB>N` partial that the driver folds into one review-trigger total.
_whitebox_scan_module() {
  local module="$1" root="$2"
  local sb="$root/$module/target/spotbugsXml.xml"
  local cs="$root/$module/target/checkstyle-result.xml"
  local pmd="$root/$module/target/pmd.xml"
  local report line count

  for report in spotbugsXml.xml pmd.xml checkstyle-result.xml; do
    if [[ ! -f "$root/$module/target/$report" ]]; then
      printf 'FAIL\t%s\tmissing %s; run ./mvnw -Pquality verify before gate\n' "$module" "$module/target/$report"
    fi
  done

  if [[ -f "$sb" ]]; then
    while IFS= read -r line; do
      printf 'FAIL\t%s\tSpotBugs high-confidence finding: %s\n' "$module/target/spotbugsXml.xml" "$line"
    done < <({ grep -nE '<BugInstance[^>]*(priority="1"|rank="([1-4])")' "$sb" 2>/dev/null || true; } | head -20)
  fi

  if [[ -f "$cs" ]]; then
    while IFS= read -r line; do
      printf 'FAIL\t%s\tCheckstyle hard-style finding: %s\n' "$module/target/checkstyle-result.xml" "$line"
    done < <({ grep -nE '<error[^>]*severity="error"' "$cs" 2>/dev/null || true; } | head -40)
  fi

  if [[ -f "$pmd" ]]; then
    count=$(grep -c '<violation ' "$pmd" 2>/dev/null || true)
    printf '__PMD__\t%s\n' "${count:-0}"
  fi
}

check_whitebox_quality_reports() {
  local root tmpdir module safe pmd_total partial
  root="$(_whitebox_repo_root)"
  tmpdir="$(mktemp -d)"

  # Fan out one backgrounded worker per Java module.
  while IFS= read -r module; do
    [[ -z "$module" ]] && continue
    safe="${module//\//_}"
    _whitebox_scan_module "$module" "$root" > "$tmpdir/$safe.out" 2>/dev/null &
  done < <(_whitebox_java_modules)

  # Explicit join (Rule G-10 sanctioned mechanism: background jobs + wait).
  wait

  # Aggregate in stable module order so stdout is deterministic across runs.
  pmd_total=0
  while IFS= read -r module; do
    [[ -z "$module" ]] && continue
    safe="${module//\//_}"
    [[ -f "$tmpdir/$safe.out" ]] || continue
    grep -vE '^__PMD__' "$tmpdir/$safe.out" 2>/dev/null || true
    partial=$(grep -E '^__PMD__' "$tmpdir/$safe.out" 2>/dev/null | sed 's/^__PMD__\t//' || true)
    [[ -n "$partial" ]] && pmd_total=$((pmd_total + partial))
  done < <(_whitebox_java_modules)

  rm -rf "$tmpdir"

  if [[ "${pmd_total:-0}" != "0" ]]; then
    printf 'INFO\tpmd\tPMD review-trigger findings: %s\n' "$pmd_total"
  fi
}
