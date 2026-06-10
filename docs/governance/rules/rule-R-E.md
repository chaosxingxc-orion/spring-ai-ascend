---
rule_id: R-E
title: "Three-Track Channel Isolation"
level: L1
view: physical
principle_ref: P-E
authority_refs: [ADR-0069]
enforcer_refs: [E64]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Cross-service internal communication MUST be sliced into three physically isolated channels declared in `docs/governance/bus-channels.yaml`: `control` (out-of-band, highest priority), `data` (in-band, heavy-load), and `rhythm` (heartbeat/liveness). No two channels may share a `physical_channel:` identifier. The `data` channel inherits the 16 KiB inline-payload cap from §4 #13.**
deferred_sub_clauses:
  - id: ".b"
    title: "Three-Track Channel Physical Implementation [Deferred to W2]"
    re_introduction_trigger: "first deployable `agent-bus-java` reactor module shipping in research/prod posture with > 1 service instance (target: W2)."
    deferred_body: |
      **Rule (draft)**: Each of the three channels declared in `docs/governance/bus-channels.yaml` MUST be backed by a distinct physical transport — Kafka topics with isolated partitions, separate Redis Streams, OR equivalent broker primitives. The `physical_channel:` identifier in the YAML MUST map to a concrete broker resource. Co-locating two channels on the same physical transport (even with different routing keys) is forbidden — the failure-isolation guarantee requires distinct underlying queues.

      Composes with: ARCHITECTURE.md §6.4; ADR-0069; Rule R-E; LucioIT W1 §6.4.
    relates_to: ["ADR-0069", "Rule R-E", "ARCHITECTURE.md §6.4", "LucioIT W1 §6.4"]
---

## Motivation

The L0 motivation (LucioIT W1 §6.4): any single network-congestion event must NOT cause global paralysis. A slow text-to-video transfer on `data` cannot block a `PAUSE` intent on `control`.

## Cross-references

- Enforced by Gate Rule R-M sub-clause .c (`bus_channels_three_track_present`) — schema check on the YAML and uniqueness of `physical_channel`.
- Architecture reference: ADR-0069 / LucioIT W1 §6.4.
- Physical channel implementation deferred to W2 per `CLAUDE-deferred.md` 35.b.
- Cross-cited by Rule R-M sub-clause .d ([`rule-R-M.md`](rule-R-M.md)) envelope-propagation matrix (S2C callback inter-rule co-design).
- Related: Rule R-K ([`rule-R-K.md`](rule-R-K.md)) — Skill Capacity Matrix (capacity arbitration coordinates with channel discipline).

## Deferred sub-clauses

Rule R-E.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
