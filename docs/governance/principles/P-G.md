---
principle_id: P-G
title: "Absolute Non-Blocking I/O"
level: L0
view: process
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-G]   # formerly Rule 37 (numeric pre-ADR-0086)
kernel: |
  P-G — Absolute Non-Blocking I/O.
  External I/O calls (model gateway, vector DB, sandbox dispatch) MUST use
  Reactive or Virtual Threads.
  The OS-level worker thread MUST be released during the I/O wait so other
  Agents can proceed.
  Enforced by Rule R-G.
  *(v2.0.0-rc3 honesty: the W2.x synchronous S2C bridge in
  `SyncOrchestrator.handleClientCallback` blocks on
  `.toCompletableFuture().join()` — this is a deliberately deferred exception
  tracked under Rule R-M sub-clause .d.c. Production deployments that need non-blocking S2C
  must wait for the W2 async orchestrator.)*
scope_phase: design
---

## Motivation

This principle exists because **a single blocking external call holds an OS thread for tens of seconds**; with ~10 stuck calls a 256-thread cluster paralyses, with ~100 it dies. Reactive (`WebClient` / `R2dbcEntityTemplate`) or Virtual-Thread-backed clients release the OS thread during the I/O wait, letting other Agents proceed on the freed thread. The doctrine is also paired with **operational honesty** — the v2.0.0-rc3 SyncOrchestrator bridge knowingly blocks during the S2C suspend/resume turn and is tracked under Rule R-M sub-clause .d.c rather than hidden as compliant, so that operators understand the deployment posture before they hit production load.

## Operationalising rules

- Rule R-G — Reactive External I/O ([`docs/governance/rules/rule-R-G.md`](../rules/rule-R-G.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §6.3 absolute-non-blocking doctrine)
- Deferred sub-clause 37.c — `agent-platform` `JdbcTemplate` uses (HealthCheckRepository, PlatformOssApiProbe) migrate to R2DBC in W2; legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Deferred sub-clause 46.c — synchronous S2C bridge non-blocking lifecycle (W2 async orchestrator)
- Related: P-H (Chronos Hydration) — sleeping declaratively is the second half of "never hold a thread"
- Related: P-K (Skill Capacity) — skill-pool exhaustion suspends agents instead of blocking threads
