#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 47 — no_blocking_io_in_runtime_main. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 47 — no_blocking_io_in_runtime_main (enforcer E66, Rule 37 / P-G)
#
# No production class under agent-service/src/main/java/** may import
# org.springframework.web.client.RestTemplate or
# org.springframework.jdbc.core.JdbcTemplate. Scope is intentionally narrow
# to agent-runtime (the cognitive kernel). Existing agent-platform JdbcTemplate
# uses migrate to R2DBC in W2 per CLAUDE-deferred.md 37.c.
# ---------------------------------------------------------------------------
_r47_fail=0
# Scope NARROWED post-Phase-C (ADR-0078): Rule 37 applies to the runtime sub-
# package only. agent-service/src/main/java/com/huawei/ascend/service/platform/**
# is excluded per CLAUDE-deferred.md 37.c — the platform-side JdbcTemplate uses
# (HealthCheckRepository, PlatformOssApiProbe) migrate to R2DBC in W2.
_r47_root="agent-service/src/main/java/com/huawei/ascend/service/runtime"
if [[ -d "$_r47_root" ]]; then
  _r47_hits="$(grep -rEln '^import[[:space:]]+org\.springframework\.(web\.client\.RestTemplate|jdbc\.core\.JdbcTemplate);' "$_r47_root" 2>/dev/null || true)"
  if [[ -n "$_r47_hits" ]]; then
    while IFS= read -r _f; do
      [[ -z "$_f" ]] && continue
      fail_rule "no_blocking_io_in_runtime_main" "$_f imports a forbidden blocking-I/O client (RestTemplate or JdbcTemplate) — use WebClient or R2dbcEntityTemplate instead"
      _r47_fail=1
    done <<< "$_r47_hits"
  fi
fi
if [[ $_r47_fail -eq 0 ]]; then pass_rule "no_blocking_io_in_runtime_main"; fi

# ---------------------------------------------------------------------------
