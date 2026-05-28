---
index_id: DISCOVERY-RULE-INDEX
governance_infra: true
generated_at: 2026-05-28
generator: "spring-ai-ascend Phase A Wave 3"
purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."
---

# Rule Discovery Index

- **schema_version**: 1
- **last_updated**: 2026-05-28
- **count**: 52

## Usage

This file is a Tier-2 progressive-disclosure index over the governance rule cards under `docs/governance/rules/`. Each line names one rule by id (D-/G-/R-/M- namespace), its canonical card path, the rule's current `status:` value, and a `product_claim` tag — either an actual `PC-NNN` id, `governance_infra` (when the rule governs framework discipline rather than a product capability), or `placeholder` (Wave-4 backfill target).

Load this index to find rules by id, topic, or status without scanning every card. Each rule's full body (motivation, details, enforcers, exit criteria) lives behind the linked card and is loaded on demand by the `/design-mode`, `/impl-mode`, `/verify-mode`, `/commit-mode`, and `/review-mode` phase-contract skills declared in `CLAUDE.md`. Sort order: namespace (D, G, M, R) then numeric/letter suffix.

## Index

- [Rule D-1](docs/governance/rules/rule-D-1.md) — Root-Cause + Strongest-Interpretation Before Plan — active — product_claim:placeholder
- [Rule D-2](docs/governance/rules/rule-D-2.md) — Simplicity & Surgical Changes — active — product_claim:placeholder
- [Rule D-3](docs/governance/rules/rule-D-3.md) — Pre-Commit Checklist + Evidence-First Debug — active — product_claim:placeholder
- [Rule D-4](docs/governance/rules/rule-D-4.md) — Three-Layer Testing, With Honest Assertions — active — product_claim:placeholder
- [Rule D-5](docs/governance/rules/rule-D-5.md) — Self-Audit is a Ship Gate, Not a Disclosure — active — product_claim:placeholder
- [Rule D-6](docs/governance/rules/rule-D-6.md) — Posture-Aware Defaults — active — product_claim:placeholder
- [Rule D-7](docs/governance/rules/rule-D-7.md) — Concurrency / Async Resource Lifetime — active — product_claim:placeholder
- [Rule D-8](docs/governance/rules/rule-D-8.md) — Single Construction Path Per Resource Class — active — product_claim:placeholder
- [Rule D-9](docs/governance/rules/rule-D-9.md) — No Version / Log Metadata in Code — unknown — product_claim:placeholder
- [Rule G-1](docs/governance/rules/rule-G-1.md) — Layered 4+1 Discipline + Architecture Workspace Truth — active — product_claim:placeholder
- [Rule G-1.1](docs/governance/rules/rule-G-1.1.md) — L1 Architecture Depth & Grounding — active — product_claim:placeholder
- [Rule G-2](docs/governance/rules/rule-G-2.md) — Authority-Text Reality (doc / status / path / numeric truth) — active — product_claim:placeholder
- [Rule G-2.1](docs/governance/rules/rule-G-2.1.md) — Deleted-Module Scope Prevention — active — product_claim:placeholder
- [Rule G-3](docs/governance/rules/rule-G-3.md) — Kernel-Card-Implementation Coherence — active — product_claim:placeholder
- [Rule G-3.1](docs/governance/rules/rule-G-3.1.md) — Kernel-Implementation Disjunction Truth — active — product_claim:placeholder
- [Rule G-4](docs/governance/rules/rule-G-4.md) — Always-Loaded Context Budget — active — product_claim:placeholder
- [Rule G-5](docs/governance/rules/rule-G-5.md) — Gate Self-Consistency (parity / coverage / manifest / freshness) — active — product_claim:placeholder
- [Rule G-6](docs/governance/rules/rule-G-6.md) — Gate Machinery Integrity (duration + config) — active — product_claim:placeholder
- [Rule G-7](docs/governance/rules/rule-G-7.md) — Linux-First Dev Environment — active — product_claim:placeholder
- [Rule G-8](docs/governance/rules/rule-G-8.md) — Cross-Authority Parity (graph baseline / SPI path / module topology / current-claim grammar / structural-carrier parity) — active — product_claim:placeholder
- [Rule G-9](docs/governance/rules/rule-G-9.md) — Recurring-Defect Family Truth — active — product_claim:placeholder
- [Rule G-10](docs/governance/rules/rule-G-10.md) — Parallel-Linux-Scripts Mandate — active — product_claim:placeholder
- [Rule G-11](docs/governance/rules/rule-G-11.md) — Phase-Contract Rule-Allocation Coherence — active — product_claim:placeholder
- [Rule G-12](docs/governance/rules/rule-G-12.md) — Whitebox Quality Baseline — active — product_claim:placeholder
- [Rule G-13](docs/governance/rules/rule-G-13.md) — Single-Source Rendering Coherence — active — product_claim:placeholder
- [Rule G-14](docs/governance/rules/rule-G-14.md) — Feature Lifecycle Validity — active_advisory — product_claim:placeholder
- [Rule G-15](docs/governance/rules/rule-G-15.md) — Fact-Layer Integrity — active_advisory — product_claim:placeholder
- [Rule G-16](docs/governance/rules/rule-G-16.md) — ProductClaim Referential Integrity — active — product_claim:governance_infra
- [Rule G-17](docs/governance/rules/rule-G-17.md) — No Orphan Artefacts — active — product_claim:governance_infra
- [Rule G-18](docs/governance/rules/rule-G-18.md) — Traceability Chain Completeness — active — product_claim:governance_infra
- [Rule G-19](docs/governance/rules/rule-G-19.md) — Auto-Load Tier Integrity — active — product_claim:governance_infra
- [Rule G-20](docs/governance/rules/rule-G-20.md) — Governance-Infra Honesty — active — product_claim:governance_infra
- [Rule G-21](docs/governance/rules/rule-G-21.md) — ProductClaim Placeholder Decreasing — active — product_claim:governance_infra
- [Rule M-1](docs/governance/rules/rule-M-1.md) — Skeleton Module Has No Production Java — active — product_claim:placeholder
- [Rule M-2](docs/governance/rules/rule-M-2.md) — Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth) — active — product_claim:placeholder
- [Rule R-A](docs/governance/rules/rule-R-A.md) — Business/Platform Decoupling Enforcement — active — product_claim:placeholder
- [Rule R-A.c](docs/governance/rules/rule-R-A.c.md) — Quickstart CI Smoke Run — active — product_claim:placeholder
- [Rule R-B](docs/governance/rules/rule-R-B.md) — Competitive Baselines Required — active — product_claim:placeholder
- [Rule R-C](docs/governance/rules/rule-R-C.md) — Code-as-Contract — active — product_claim:placeholder
- [Rule R-C.1](docs/governance/rules/rule-R-C.1.md) — Independent Module Evolution — active — product_claim:placeholder
- [Rule R-C.2](docs/governance/rules/rule-R-C.2.md) — Run Contract Spine — active — product_claim:PC-001|PC-003
- [Rule R-D](docs/governance/rules/rule-R-D.md) — SPI + DFX + TCK Co-Design + Catalog Integrity — active — product_claim:placeholder
- [Rule R-E](docs/governance/rules/rule-R-E.md) — Three-Track Channel Isolation — active — product_claim:placeholder
- [Rule R-F](docs/governance/rules/rule-R-F.md) — Cursor Flow Mandate — active — product_claim:placeholder
- [Rule R-G](docs/governance/rules/rule-R-G.md) — Reactive External I/O — active — product_claim:placeholder
- [Rule R-H](docs/governance/rules/rule-R-H.md) — No Thread.sleep in Business Code — active — product_claim:placeholder
- [Rule R-I](docs/governance/rules/rule-R-I.md) — Five-Plane Manifest — active — product_claim:placeholder
- [Rule R-I.1](docs/governance/rules/rule-R-I.1.md) — Edge↔Compute Ingress Routing — design_only — product_claim:placeholder
- [Rule R-J](docs/governance/rules/rule-R-J.md) — Storage-Engine Tenant Isolation + Cancel Re-Authorization — active — product_claim:placeholder
- [Rule R-K](docs/governance/rules/rule-R-K.md) — Skill Capacity Matrix — active — product_claim:placeholder
- [Rule R-L](docs/governance/rules/rule-R-L.md) — Sandbox Permission Subsumption — active — product_claim:placeholder
- [Rule R-M](docs/governance/rules/rule-R-M.md) — Engine Contract (envelope / matching / hooks / S2C / scope / historical) — active — product_claim:placeholder
