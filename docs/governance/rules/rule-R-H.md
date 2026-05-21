---
rule_id: R-H
title: "No Thread.sleep in Business Code"
level: L1
view: process
principle_ref: P-H
authority_refs: [ADR-0069]
enforcer_refs: [E67]
status: active
scope_phase: impl
kernel_cap: 8
kernel: |
  **No production class under `agent-service/src/main/java/ascend/springai/service/platform/**` or `agent-service/src/main/java/ascend/springai/service/runtime/**` may invoke `Thread.sleep(...)` or `TimeUnit.<unit>.sleep(...)`. Long-horizon waits MUST be expressed as declarative suspension (`SuspendSignal`) and resumed by the bus-level Tick Engine.**
---

## Motivation

The L0 motivation (LucioIT W1 §6.4): physical sleep holds a thread for the wait duration; with 1000 sleeping agents, the system is paralysed. Chronos Hydration self-destructs the sleeping process and re-hydrates it on the bus wake-pulse.

## Cross-references

- Enforced by Gate Rule M-2 sub-clause .a (`no_thread_sleep_in_business_code`) — source scan for `Thread.sleep` and `TimeUnit.<x>.sleep`. Test code (`src/test/java`), gate scripts, and Awaitility usage are excluded.
- Architecture reference: ADR-0069 / LucioIT W1 §6.4.
- Cross-cited by Rule R-M sub-clause .d ([`rule-R-M.md`](rule-R-M.md)) envelope-propagation matrix and asserted independently for the S2C surface by E83 (`S2cCallbackRespectsRule38Test` — no Thread.sleep in s2c..).
- Companion rule: Rule R-K ([`rule-R-K.md`](rule-R-K.md)) — Skill Capacity Matrix (at W1 over-cap callers receive a `SkillResolution.reject(SuspendReason.RateLimited)` decision envelope; actual `Run`/dependent-step `SUSPENDED` transition is deferred to Rule R-K.c via the same Chronos Hydration interlock at W2 scheduler admission per ADR-0070/ADR-0091).
