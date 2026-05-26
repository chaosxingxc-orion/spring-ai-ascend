---
level: L1
view: development
module: agent-service
status: skeleton
authority: "ADR-0078 (consolidation) + ADR-0099 (rc22 — Rule G-1.1 L1 depth + grounding) + ADR-0100 (rc22 — 5-component decomposition) + ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0140 (rc55 — Engine Adapter split 5a/5b) + ADR-0141 (rc55 — Internal Event Queue design_only; service.queue/ NOT shown in tree) + ADR-0144 (rc55 — Layer↔Package matrix)"
---

# agent-service — Development View

> Wave: rc55 W2 skeleton. Content arrives in rc55 W5.
> Authoring source: agent-service/ARCHITECTURE.md §12 (current Development View tree) + review file §18, ported with rc55 corrections (R7 layer↔package matrix per ADR-0144, M9 dispatcher boundary clarification, R2 service.queue/ NOT shown, M8 logical-layer ↔ package-tree mapping, 5 L2 boundary contracts per ADR-0141 + review §20).

## 1. Target Directory Tree (Rule G-1.1.a)

TODO Wave 5. Cross-walked against filesystem at gate time.

## 2. Layer ↔ Package Matrix (ADR-0144)

TODO Wave 5.

## 3. L2 Boundary Contracts (Rule G-1.1.c)

TODO Wave 5. Boundary contracts for 5 L2 zones:
- L2.1 — Run lifecycle extended for Session decoupling (rc25 candidate per review §20)
- L2.2 — Reactive Orchestrator backpressure protocol (rc23-25 candidate)
- L2.3 — Postgres RLS migration sequence (rc25 candidate)
- L2.4 — DualTrackRouter predicate refinement (W2 candidate per ADR-0112)
- L2.5 — Internal Event Queue Boundary Contract (ADR-0141 — published at design time even though no L2 doc exists yet)
