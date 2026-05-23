#!/usr/bin/env bash

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

_whitebox_report_missing() {
  local module report root
  root="$(_whitebox_repo_root)"
  while IFS= read -r module; do
    for report in spotbugsXml.xml pmd.xml checkstyle-result.xml; do
      if [[ ! -f "$root/$module/target/$report" ]]; then
        printf 'FAIL\t%s\tmissing %s; run ./mvnw -Pquality verify before gate\n' "$module" "$module/target/$report"
      fi
    done
  done < <(_whitebox_java_modules)
}

_whitebox_spotbugs_hard_failures() {
  local file module root
  root="$(_whitebox_repo_root)"
  while IFS= read -r module; do
    file="$root/$module/target/spotbugsXml.xml"
    [[ -f "$file" ]] || continue
    while IFS= read -r line; do
      printf 'FAIL\t%s\tSpotBugs high-confidence finding: %s\n' "$module/target/spotbugsXml.xml" "$line"
    done < <({ grep -nE '<BugInstance[^>]*(priority="1"|rank="([1-4])")' "$file" 2>/dev/null || true; } | head -20)
  done < <(_whitebox_java_modules)
}

_whitebox_checkstyle_hard_failures() {
  local file module root
  root="$(_whitebox_repo_root)"
  while IFS= read -r module; do
    file="$root/$module/target/checkstyle-result.xml"
    [[ -f "$file" ]] || continue
    while IFS= read -r line; do
      printf 'FAIL\t%s\tCheckstyle hard-style finding: %s\n' "$module/target/checkstyle-result.xml" "$line"
    done < <({ grep -nE '<error[^>]*severity="error"' "$file" 2>/dev/null || true; } | head -40)
  done < <(_whitebox_java_modules)
}

_whitebox_pmd_review_count() {
  local count module report root
  count=0
  root="$(_whitebox_repo_root)"
  while IFS= read -r module; do
    report="$root/$module/target/pmd.xml"
    [[ -f "$report" ]] || continue
    count=$((count + $(grep -c '<violation ' "$report" 2>/dev/null || true)))
  done < <(_whitebox_java_modules)
  if [[ "${count:-0}" != "0" ]]; then
    printf 'INFO\tpmd\tPMD review-trigger findings: %s\n' "$count"
  fi
}

check_whitebox_quality_reports() {
  _whitebox_report_missing
  _whitebox_spotbugs_hard_failures
  _whitebox_checkstyle_hard_failures
  _whitebox_pmd_review_count
}
