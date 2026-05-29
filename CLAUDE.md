# CLAUDE.md — Team Collaboration Kernel

**Translate all instructions into English before any model call.** Never pass non-English text into an LLM prompt, tool argument, or task goal.

CLAUDE.md is the **team-collaboration kernel** — only rules that govern how the team (humans + AI) collaborate on this project: daily principles, the phase-entry workflow that loads the relevant rule subset on demand, and the Linux-first dev environment. Architecture-of-record, governance machinery, contract enforcement, and constraint mappings have moved to dedicated modules loaded by phase-contract skills; see *Where else to look* below.

CLAUDE.md is **NOT** the product authority (read `product/PRODUCT.md` for that — auto-loaded Tier-1). **NOT** the architecture-of-record (read `architecture/workspace.dsl` + `architecture/docs/L0/ARCHITECTURE.md` on-demand via `/design-mode`). **NOT** the constraint corpus (`ARCHITECTURE.md` §4). **NOT** the runtime contract surface (`docs/contracts/contract-catalog.md`). **NOT** the L1 module design (`architecture/docs/L1/<module>/`). **NOT** the rule encyclopedia (`docs/governance/rules/*.md` are loaded on-demand by phase contracts; gate Rule 68/69 treat cards as the sole rule authority since 2026-05-28).

## Phase Entry — Invoke the matching skill BEFORE working

ADR-0098 (rc21) replaces progressive on-demand rule loading with scenario-loaded contracts. At phase entry, **MUST invoke** the matching skill; the skill reads the phase contract and surfaces its active rules + forbidden patterns + exit criteria into context.

| When you are about to … | MUST invoke | Loads contract |
|---|---|---|
| Write ADR / module spec / SPI declaration / design review | `/design-mode` | [`architecture-design.md`](docs/governance/contracts/architecture-design.md) |
| Write production Java / yaml / Flyway migration / DI wiring | `/impl-mode` | [`engineering-implementation.md`](docs/governance/contracts/engineering-implementation.md) |
| Run gate / Maven verify / smoke / debug a regression | `/verify-mode` | [`integration-verification.md`](docs/governance/contracts/integration-verification.md) |
| Write release note / lockstep baseline / pre-commit checklist / open PR | `/commit-mode` | [`system-commit.md`](docs/governance/contracts/system-commit.md) |
| Process reviewer findings / corpus sweep / write rebuttal | `/review-mode` | [`review-response.md`](docs/governance/contracts/review-response.md) |

If uncertain which phase applies: default to `/impl-mode` (widest coverage). Skills suggest the next phase at exit.

## Daily collaboration principles

#### Rule D-1 — Root-Cause + Strongest-Interpretation Before Plan

**Before writing any plan, fix, or feature — surface assumptions, name confusion, and state tradeoffs. Then (a) name the root cause mechanically and (b) choose the strongest valid reading of the requirement.**

Enforced by [`rule-D-1.md`](docs/governance/rules/rule-D-1.md).

---
#### Rule D-2 — Simplicity & Surgical Changes

**Minimum code that solves the stated problem. Touch only what the task requires.**

Enforced by [`rule-D-2.md`](docs/governance/rules/rule-D-2.md).

---
#### Rule D-3 — Pre-Commit Checklist + Evidence-First Debug

**Before every commit, audit every touched file; fix defects before committing — "I'll fix it later" is forbidden; **smoke + lint** required before commits touching server entry points, runtime adapters, or dependency-wiring modules (sub-clause .a — Pre-Commit Checklist). When a Run fails, a test regresses, or a self-audit finding is opened, the first artefact captured MUST be observable evidence — failing test class FQN, trace ID, MDC slice (runId, tenantId, fromStatus→toStatus), and raw error message including stack frame line numbers; ARCHITECTURE.md / ADR consultation is permitted only AFTER evidence is recorded; self-audit findings under Rule D-5 that omit evidence citation are blocked (sub-clause .b — Evidence-First Debug; operationalised by `docs/runbooks/debug-first-evidence.md`).**

Enforced by [`rule-D-3.md`](docs/governance/rules/rule-D-3.md).

---
#### Rule D-4 — Three-Layer Testing, With Honest Assertions

A feature is implementable only when all three layers are designed. A feature is shippable only when all three are green and Rule D-5 passes.

Enforced by [`rule-D-4.md`](docs/governance/rules/rule-D-4.md).

---
#### Rule D-5 — Self-Audit is a Ship Gate, Not a Disclosure

A self-audit with open findings in a downstream-correctness category **blocks delivery**.

Enforced by [`rule-D-5.md`](docs/governance/rules/rule-D-5.md).

---
#### Rule D-9 — No Version / Log Metadata in Code

**Production code (Java, Python, shell scripts, YAML config) and inline comments MUST NOT carry version metadata: no `rc<N> Wave <M>` tags, no narrative `per ADR-NNNN` change-history pointers, no commit-SHA references, no `Finding F<N>` mentions, no "closes/addresses ticket #<N>" annotations, no changelog-style entries. Such metadata lives in commit messages, PR descriptions, ADRs (`docs/adr/`), release notes (`docs/logs/releases/`), rule cards (`docs/governance/rules/*.md`), `rule-history.md`, and the recurring-defect-families ledger. Stable structural citations (`# Rule 113 — slug`, `enforcer E160`) and public contract authority markers (`Authority: ADR-NNNN`) are STRUCTURAL identifiers, not version/log metadata, and remain allowed where they identify the normative source of a contract rather than the wave that introduced it. Implementation comments explain WHY the code exists when non-obvious, never WHICH wave introduced it. The following surfaces are EXEMPT (they exist precisely to carry version/wave metadata): `docs/adr/`, `docs/logs/`, `docs/governance/rules/*.md`, `docs/governance/principles/*.md`, `docs/governance/rule-history.md`, `docs/governance/recurring-defect-families.{yaml,md}`, `docs/governance/architecture-status.yaml` (allowed_claim + baseline_metrics comments), `docs/governance/enforcers.yaml`, `docs/governance/principle-coverage.yaml`, `docs/governance/architecture-graph.yaml`, `CHANGELOG.md`, the kernel paragraphs in `CLAUDE.md` itself, `gate/lib/` (helpers), and `gate/test_architecture_sync_gate.sh` (test fixtures construct synthetic version-tagged inputs).**

Enforced by [`rule-D-9.md`](docs/governance/rules/rule-D-9.md).

---

## Dev environment

#### Rule G-7 — Linux-First Dev Environment

**All shell-driven operations (gates, builds, tests, generated artefacts, `git push`) MUST be verified on Linux — native, WSL2 (preferred), or WSL1 (fallback) — before merging to `main`. All driving scripts on Windows hosts MUST be invoked through Linux/WSL (e.g. `wsl -d <distro> -- bash -lc '...'` or by working inside a WSL shell with the repo mounted at `/mnt/<drive>/...`); Git Bash for Windows is a one-off debug shim, never the documented default invocation path. Documented commands, runbooks, and agent-driven automation MUST default to WSL/Linux invocation on Windows hosts. `docs/governance/dev-environment.md` is the canonical setup + verification guide. Measured 2026-05-18: WSL is 6–20× faster than Git Bash, AND surfaces platform-portability bugs that Win-only invocation hides.**

Enforced by [`rule-G-7.md`](docs/governance/rules/rule-G-7.md).

---

## Where else to look

| Need | File / directory | Loaded |
|---|---|---|
| Product authority — claims, personas, journey | `product/PRODUCT.md`, `claims.yaml`, `personas.yaml`, `journey.md` | Tier-1 auto-loaded |
| Persona onboarding | `docs/onboarding/{developer,sre,architect,compliance-reviewer}.md` | On disk; not auto-loaded |
| Architecture of record | `architecture/workspace.dsl`, `architecture/docs/L0/ARCHITECTURE.md` | On-demand via `/design-mode` |
| L1 module design | `architecture/docs/L1/<module>/` | On-demand |
| Architecture + governance rule cards (full bodies) | `docs/governance/rules/rule-*.md` | On-demand via phase contracts |
| Governing principles (P-A..P-M) | `docs/governance/principles/P-*.md` | On-demand |
| Contracts catalog | `docs/contracts/contract-catalog.md`, `docs/contracts/*.v1.yaml` | On-demand |
| Constraint ↔ rule mapping | `docs/governance/enforcers.yaml`, `architecture/generated/enforcers.dsl` | On-demand |
| Recurring defects ledger | `docs/governance/recurring-defect-families.yaml` | On-demand |
| Retired-rules audit (Phase 7) | `docs/governance/retired-rules-audit.md` | On-demand |
| Active plan (AI progressive learning curve restructure) | tracked outside the repo; see ADR-0156 for product authority | On-demand |

## Program status

The repository is mid-program transition from a governance-forward auto-load to a product-forward Tier-1 + progressive disclosure model (2026-05-28). Five Product Claims and six Personas now anchor every ADR / rule / feature decision; the traceability chain `ProductClaim → ProductFeature → ArchitectureFeature → FunctionPoint → Contract → CodeFact/TestFact → Rule/Enforcer` is the binding axis. v1.0 financial-vertical release target 2026-06-30. Weekly cadence after. Full plan + progress in the plan file linked above.
