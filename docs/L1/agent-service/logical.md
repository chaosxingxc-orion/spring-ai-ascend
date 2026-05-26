---
level: L1
view: logical
module: agent-service
status: skeleton
authority: "ADR-0138 (rc53 — 5-layer L1 ratification) + ADR-0140 (rc55 — Engine Adapter Layer split into 5a + 5b) + ADR-0141 (rc55 — Internal Event Queue design_only) + ADR-0142 (rc55 — Run aggregate single owner) + ADR-0144 (rc55 — Layer↔Package matrix) + ADR-0145 (rc55 — sealed RunEvent hierarchy)"
---

# agent-service — Logical View

> Wave: rc55 W2 skeleton. Content arrives in rc55 W3.
> Authoring source: review file `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md` §15, ported with rc55 corrections (R1 split, R3 single-owner, R4 distinct-mechanisms, R8 sealed RunEvent diagram) per the rc55 plan.

## 1. Five-Layer Component Diagram

TODO Wave 3. Will incorporate:
- ADR-0140 Layer 5 split (5a Engine Dispatch + 5b Translation/Tool-Intercept)
- ADR-0142 Run aggregate pinning (Layer 2 owns; Layer 4 uses typed reference)
- ADR-0141 Layer 3 demoted to design_only sub-section (NOT a peer layer in the diagram)
- ADR-0145 RunEvent variants annotated on layer interactions

## 2. ER Model — Run / Task / Session / LifecycleState (tenantId-first)

TODO Wave 3.

## 3. RunStatus State Machine (cancel-race-aware, CAS-annotated)

TODO Wave 3.

## 4. Task.A2aState State Machine (A2A Protocol Envelope)

TODO Wave 3.

## 5. SuspendSignal Flow (Child-Run + S2C-Callback Variants)

TODO Wave 3.

## 6. RunEvent Sealed Hierarchy (per ADR-0145)

TODO Wave 3.

## 7. Vocabulary Glossary (PR #71 ↔ Shipped, per ADR-0136 + ADR-0137)

TODO Wave 3.
