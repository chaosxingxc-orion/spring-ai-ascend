---
rule_id: 35
title: "Three-Track Channel Isolation"
level: L1
view: physical
principle_ref: P-E
authority_refs: [ADR-0069]
enforcer_refs: [E64]
status: active
kernel_cap: 8
kernel: |
  **Cross-service internal communication MUST be sliced into three physically isolated channels declared in `docs/governance/bus-channels.yaml`: `control` (out-of-band, highest priority), `data` (in-band, heavy-load), and `rhythm` (heartbeat/liveness). No two channels may share a `physical_channel:` identifier. The `data` channel inherits the 16 KiB inline-payload cap from §4 #13.**
---

## Motivation

The L0 motivation (LucioIT W1 §6.4): any single network-congestion event must NOT cause global paralysis. A slow text-to-video transfer on `data` cannot block a `PAUSE` intent on `control`.

## Cross-references

- Enforced by Gate Rule 45 (`bus_channels_three_track_present`) — schema check on the YAML and uniqueness of `physical_channel`.
- Architecture reference: ADR-0069 / LucioIT W1 §6.4.
- Physical channel implementation deferred to W2 per `CLAUDE-deferred.md` 35.b.
- Cross-cited by Rule 46 ([`rule-46.md`](rule-46.md)) envelope-propagation matrix (S2C callback inter-rule co-design).
- Related: Rule 41 ([`rule-41.md`](rule-41.md)) — Skill Capacity Matrix (capacity arbitration coordinates with channel discipline).

## Deferred sub-clauses

Rule 35.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
