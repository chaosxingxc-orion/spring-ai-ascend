---
principle_id: P-E
title: "Multi-Track Bus Physical Channel Isolation"
level: L0
view: physical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-E]   # formerly Rule 35 (numeric pre-ADR-0086)
product_claim: "PC-002"
kernel: |
  P-E — Multi-Track Bus Physical Channel Isolation.
  Cross-service internal communication is sliced into three physically
  isolated channels — `control` (out-of-band PAUSE/KILL/CANCEL intents,
  highest priority), `data` (in-band heavy-load payload bodies),
  and `rhythm` (heartbeat / liveness pulses).
  No congestion on one channel can paralyse another.
  Enforced by Rule R-E.
scope_phase: design
---

## Motivation

This principle exists because a single shared bus channel under load **paralyses control intents alongside data payloads** — a slow text-to-video transfer on `data` blocks a `PAUSE` intent that should arrive instantly, and the operator loses the ability to recover. The three-track split is the minimum physical isolation that lets `control` traffic preempt `data` traffic regardless of saturation, and lets `rhythm` heartbeats survive even when both control and data are degraded so the supervisor can still observe liveness. The `data` channel inherits the 16 KiB inline-payload cap (§4 #13) to bound how badly any single payload can hog its own track.

## Operationalising rules

- Rule R-E — Three-Track Channel Isolation ([`docs/governance/rules/rule-R-E.md`](../rules/rule-R-E.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 L0 §6–§7 absorption)
- Schema source of truth: [`docs/governance/bus-channels.yaml`](../bus-channels.yaml)
- Deferred sub-clause 35.b — physical channel implementation (W2 trigger); legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Related: P-F (Cursor Flow) — the `control` channel is the upstream side of cursor cancellation
- Related: P-K (Skill Capacity) — `data` congestion triggers skill-suspension, not bus paralysis
