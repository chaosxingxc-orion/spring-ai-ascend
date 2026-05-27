---
level: L1
view: development
module: agent-service
status: proposed
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.1-6.6 per the post-merge audit plan (Wave 3). Anchors back to canonical 4+1 views under docs/L1/agent-service/ — this directory is module-feature decomposition, NOT a new architectural authority level."
---

# agent-service — Feature Inventory (per-module decomposition)

> Scope: 47 features (AS-L1-F01..AS-L1-F47) grouped by the six service-architecture modules from ADR-0138 + ADR-0140.
> Status: `proposed` — these features describe required capability boundaries; they do NOT all map to shipped Java code today. Shipped state grounding lives in `agent-service/ARCHITECTURE.md` and the SPI Appendix at `../spi-appendix.md`.

## 1. Layout

| File | Module | Features | Canonical Layer (per ADR-0138 + ADR-0140) |
|---|---|---|---|
| [`access-layer.md`](access-layer.md) | Access Layer | AS-L1-F01..F08 (8) | Layer 1 |
| [`session-task-manager.md`](session-task-manager.md) | Session & Task Manager | AS-L1-F09..F16 (8) | Layer 2 |
| [`internal-event-queue.md`](internal-event-queue.md) | Internal Event Queue (`design_only` per ADR-0141) | AS-L1-F17..F23 (7) | Layer 3 |
| [`task-centric-control.md`](task-centric-control.md) | Task-Centric Control Layer | AS-L1-F24..F32 (9) | Layer 4 |
| [`engine-dispatch-execution.md`](engine-dispatch-execution.md) | Engine Dispatch & Execution | AS-L1-F33..F39 (7) | Layer 5a |
| [`translation-tool-intercept.md`](translation-tool-intercept.md) | Translation & Tool-Intercept | AS-L1-F40..F47 (8) | Layer 5b |

## 2. Numbering convention

- `AS-L1-F<N>` — Agent-Service L1 Feature N. Stable across waves; reorder forbidden. New features get the next free ID; deprecated features keep their ID with a `status: deprecated` row.
- `AS-SC<N>` — Agent-Service Scenario Cluster N. Defined in [`../scenarios.md`](../scenarios.md) §0.1. Each feature row's `Covered clusters` column references one or more AS-SC IDs which anchor back to canonical scenarios S1..S5 in `scenarios.md`.

## 3. Relationship to canonical 4+1 views

These feature files are an **L1 module-feature decomposition** for downstream design grounding. They do NOT replace the canonical 4+1 source under `../{scenarios,logical,process,physical,development,spi-appendix}.md`. Where this directory and the per-view files disagree, the per-view files win (same precedence rule as ADR-0143 review-record demotion).

Concretely:
- **Scenarios anchor** — every AS-L1-F row cites AS-SC IDs from `../scenarios.md`; AS-SC IDs in turn anchor to S1..S5.
- **Logical-view anchor** — module names match the 5-layer service architecture in `../logical.md` §1 (with the 5a/5b split per ADR-0140).
- **SPI anchor** — capability rows that reference a typed contract (e.g. AS-L1-F33 EngineRegistry) MUST also appear in `../spi-appendix.md` if the SPI is shipped.
- **Configuration ownership anchor** — the per-module sovereign/consumer roles in §5 of the absorbed source file are folded into `../logical.md` §10.
- **Orthogonality anchor** — the 8 boundary red-lines from §7 of the absorbed source file are folded into `../logical.md` §11.

## 4. What changes when a feature ships

When a `proposed` feature row gets shipped Java code:
- The feature stays in this file but its row gains a `Shipped at:` column noting the Java FQN.
- The shipping happens via an impl-mode wave; this directory's front-matter `status:` flips to `shipped` only when ALL rows in the file have shipped material OR the module reaches its design-only ceiling (e.g. Internal Event Queue stays `design_only` per ADR-0141 until a queue code home lands).

## 5. Out of scope

This directory does NOT contain:
- Sequence diagrams (those live in `../process.md` — P1..P6).
- ER diagrams (those live in `../logical.md` §2).
- Package trees (those live in `../development.md` §1).
- Deployment plane mapping (those live in `../physical.md` §1).
- SPI 4-way parity table (lives in `../spi-appendix.md`).

If a feature requires expressing one of those, edit the canonical per-view file and link from here.
