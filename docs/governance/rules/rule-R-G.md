---
rule_id: R-G
title: "Reactive External I/O"
level: L1
view: process
principle_ref: P-G
authority_refs: [ADR-0069]
enforcer_refs: [E66]
status: active
scope_phase: impl
kernel_cap: 8
kernel: |
  **No production class under `agent-service/src/main/java/com/huawei/ascend/service/runtime/**` may import `org.springframework.web.client.RestTemplate` or `org.springframework.jdbc.core.JdbcTemplate`. External I/O in runtime code MUST go through Reactive (`WebClient` / `R2dbcEntityTemplate`) or Virtual-Thread-backed clients.**
---

## Motivation

The L0 motivation (LucioIT W1 §6.3): a single blocking external call holds an OS thread for tens of seconds; ~10 stuck calls paralyse a 256-thread cluster. Reactive / Virtual Threads release the OS thread during the wait.

## Cross-references

- Enforced by Gate Rule R-M sub-clause .e (`no_blocking_io_in_runtime_main`) — source scan for the forbidden imports.
- Scope is intentionally narrow to the runtime kernel — post-Phase-C this is `agent-service/src/main/java/com/huawei/ascend/service/runtime/...` (consolidated from pre-Phase-C `agent-runtime` per ADR-0078). Existing JdbcTemplate uses on the platform side (`HealthCheckRepository`, `PlatformOssApiProbe` — `agent-service/src/main/java/com/huawei/ascend/service/platform/...`, consolidated from pre-Phase-C `agent-platform`) are out of scope and migrate to R2DBC in W2 per Rule R-G.c (`docs/CLAUDE-deferred.md` 37.c).
- Architecture reference: ADR-0069 / LucioIT W1 §6.3.
- Honesty note (P-G in Layer 0): the W2.x synchronous S2C bridge in `SyncOrchestrator.handleClientCallback` blocks on `.toCompletableFuture().join()` — a deliberately deferred exception tracked under Rule R-M sub-clause .d.c (W2 async orchestrator).
- Companion rule: Rule R-H ([`rule-R-H.md`](rule-R-H.md)) — No Thread.sleep in Business Code.
