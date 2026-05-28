---
principle_id: P-I
title: "Five-Plane Distributed Topology"
level: L0
view: physical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-I]   # formerly Rule 39 (numeric pre-ADR-0086)
product_claim: "PC-002"
kernel: |
  P-I — Five-Plane Distributed Topology.
  Production deployment is divided into five physically isolated planes —
  Edge Access (Client SDK), Compute & Control (Runtime + Engine),
  Bus & State Hub (Bus + Middleware persistence),
  Sandbox Execution (untrusted code), and Evolution (Python ML).
  Workloads with different characteristics MUST NOT share infrastructure.
  Enforced by Rule R-I.
scope_phase: design
---

## Motivation

This principle exists because **workloads with different characteristics — latency-sensitive HTTP, throughput-sensitive ML training, untrusted sandbox code — share noisy neighbour failure modes** when they share infrastructure. A single GPU job can starve a low-latency HTTP path; a single sandbox escape can poison the persistence plane; an evolution job can lock the bus. Five-plane isolation makes the interference impossible at the deployment layer: Edge Access stays lightweight, Compute & Control owns runtime execution, Bus & State Hub owns persistence-of-record, Sandbox Execution is segregated for untrusted code, and Evolution runs Python ML in its own plane. Every module declares its plane in `module-metadata.yaml` so the topology is auditable at build time.

## Operationalising rules

- Rule R-I — Five-Plane Manifest ([`docs/governance/rules/rule-R-I.md`](../rules/rule-R-I.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §7.1 five-plane topology)
- Manifest source of truth: every `<module>/module-metadata.yaml` declares `deployment_plane:` ∈ {`edge` | `compute_control` | `bus_state` | `sandbox` | `evolution` | `none`}
- BoMs and build-time-only modules use `deployment_plane: none`
- Related: P-J (Storage-Engine Tenant Isolation) — Bus & State Hub plane enforces RLS at storage
- Related: P-L (Sandbox Permission Subsumption) — Sandbox plane enforces physical limits
