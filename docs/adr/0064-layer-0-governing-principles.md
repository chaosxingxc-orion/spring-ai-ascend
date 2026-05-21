# ADR-0064 — Layer-0 Governing Principles + CLAUDE.md Restructure

- Status: Accepted
- Date: 2026-05-14
- Authority: User directive — "We need CLAUDE.md to unify R&D technical conventions. Things like code repository locations, historical decisions should be moved to memory or other places. I also have four core non-negotiable rules to add." Translated and formalised in this ADR.
- Scope: (a) Restructure `CLAUDE.md` into Layer-0 Governing Principles + Layer-1 Engineering Rules. (b) Add Rule R-A (Business/Platform Decoupling Enforcement) and §4 #60. Companion ADRs 0065, 0066, 0067 cover the other three governing principles.
- Cross-link: ADR-0059 (Code-as-Contract / Rule R-C.a), ADR-0065 (Competitive Baselines), ADR-0066 (Independent Module Evolution), ADR-0067 (SPI + DFX + TCK).

## Context

`CLAUDE.md` had accumulated review-cycle scaffolding ("Rules 20–21 added in third-review cycle", "Rule G-2 sub-clause .a added in fourth-review cycle"), an inline W0 posture coverage table, and a "Constraint Coverage by First Principle" meta-section. Newcomers had to wade through history to find the active rules.

Separately, the user articulated four non-negotiable governing principles (originally in colloquial Chinese, translated per the Language Rule):

- **P-A** Business/Platform Decoupling + Developer Self-Service.
- **P-B** Four Competitive Pillars: Performance, Cost, Developer Onboarding, Governance — each continuously improvable.
- **P-C** Code-as-Everything, Rapid Evolution, Independent Module Evolution, Lightweight Production-Environment Upgrades.
- **P-D** SPI-Aligned, DFX-Explicit, Specification-Driven, TCK-Tested.

Rule R-C.a (Code-as-Contract) mandates that every "must / forbidden / required" constraint MUST have an executable enforcer in the same PR. Prose-only principles are forbidden.

The two threads are coupled because adding principles without restructuring would deepen the cleanliness problem.

## Decision

### 1. Two-layer structure for `CLAUDE.md`

`CLAUDE.md` now has two named layers:

- **Layer 0 — Governing Principles (non-negotiable)** — the four principles above, expressed as framing.
- **Layer 1 — Engineering Rules (enforceable)** — the numbered rules (1–6, 9, 10, 20, 21, 25, 28 existing + 29–32 new) that operationalise the principles.

Each Layer-0 principle maps to at least one Layer-1 rule whose enforcer ships in the same PR per Rule R-C.a. Sub-clauses with no enforcer-today are staged in `docs/CLAUDE-deferred.md` with explicit re-introduction triggers.

### 2. Cleanup targets moved out of `CLAUDE.md`

- Review-cycle annotations ("added in N-th review cycle") → `docs/governance/rule-history.md`.
- "Rule 12 replaced by binary `shipped:`" sentence → `docs/governance/rule-history.md`.
- "Constraint Coverage by First Principle" section → `docs/governance/principle-coverage.md`.
- W0 posture coverage table inside Rule D-6 → `docs/governance/posture-coverage.md`.
- Rule R-C.a body condensed from 8 paragraphs to a list + 3 sentences (no normative content lost).

Normative substance (every "must / forbidden / required" clause) is preserved verbatim in Rules 1–6, 9, 10, 20, 21, 25, 28.

### 3. New Rule R-A — Business/Platform Decoupling Enforcement

> **Platform code MUST NOT contain business-specific customizations. Business and example code MUST extend the platform via SPI + `@ConfigurationProperties` only — never by patching `*.impl.*` or `com.huawei.ascend.platform..`. The platform MUST ship a runnable quickstart (`docs/quickstart.md`) referenced from `README.md` so a developer reaches first-agent execution without platform-team intervention.**

Enforcers shipped in this PR:

- E48 ArchUnit `SpiPurityGeneralizedArchTest` — any `..spi..` package free of Spring/platform/inmemory/Micrometer/OTel deps.
- E49 Gate Rule R-C.b `quickstart_present` — `docs/quickstart.md` exists and is referenced from `README.md`.

Deferred sub-clauses (in `CLAUDE-deferred.md` 29.c): quickstart smoke-run in CI (W1 trigger when CI quickstart container infra lands).

### 4. Architecture reference

ARCHITECTURE.md §4 #60 anchors the constraint to the architectural corpus.

## Alternatives considered

**Alt A — Principles only, no new Rules.** Rejected: violates Rule R-C.a's "no prose-only constraint" mandate; would create four ship-blocking findings.

**Alt B — Inline-merge into existing rules.** Rejected: scatters the four principles across rules, obscures the "non-negotiable" framing, and dilutes the meta-doc role of `CLAUDE.md`.

**Alt C — Keep history inline in `CLAUDE.md`.** Rejected: the user explicitly asked for cleanup; history is preserved in `rule-history.md`, not deleted.

## Consequences

- **Positive**: `CLAUDE.md` becomes scannable; new contributors see the active contract immediately; history is preserved for review-cycle traceability; every governing principle is backed by a Rule R-C.a enforcer.
- **Negative**: Three new docs (`rule-history.md`, `principle-coverage.md`, `posture-coverage.md`) must be maintained alongside `CLAUDE.md`; baseline counts in `README.md`, `architecture-status.yaml`, and `gate/README.md` shift (12 active rules → 16, 30 gate rules → 36, etc.).
- **Risk surfaced**: Future cleanups must not weaken normative substance; commit-body checklist + Rule R-C.a self-check + this ADR's enforcers protect against regression.

## Enforcers (Rule R-C.a)

- E48 ArchUnit `SpiPurityGeneralizedArchTest` — any SPI package purity.
- E49 Gate Rule R-C.b `quickstart_present` — quickstart exists and is README-linked.

(Companion enforcers for Rules 30/31/32 are listed in ADR-0065/0066/0067.)

## §16 Review Checklist

- [x] Layer-0 / Layer-1 separation is named and defined.
- [x] Four governing principles (P-A, P-B, P-C, P-D) are listed with one-line summaries.
- [x] Every Layer-0 principle maps to a Layer-1 rule whose enforcer ships in this PR.
- [x] Cleanup targets (history, posture table, principle coverage) have named destination docs.
- [x] No normative "must / forbidden / required" clause is dropped from Rules 1–6, 9, 10, 20, 21, 25, 28.
- [x] Deferred sub-clauses carry explicit re-introduction triggers.
- [x] §4 #60 anchors Rule R-A in the architectural corpus.
