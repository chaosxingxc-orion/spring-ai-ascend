---
level: L1
view: scenarios
status: closure
wave: rc53
release_date: 2026-05-26
authority: ADR-0136..0139 + ADR-0100 (parent ratification)
---

# rc53 — Agent Service L1 4+1 Rewrite — Closure Release Note

> **Wave family:** rc53 / `rc53/agent-service-l1-4plus1-rewrite` branch
> **Date:** 2026-05-26
> **Scope:** `agent-service` module L1 architecture documentation refresh
> **Java-impact:** Javadoc only (5 types annotated with Vocabulary Glossary paragraphs); no method signatures, fields, packages, or DB schema changed.

## Baseline Metrics (rc53)

Reflects this wave's adjustments to `docs/governance/architecture-status.yaml#baseline_metrics`. Values reproducible via `bash gate/check_parallel.sh` + `python gate/build_architecture_graph.py --check --no-write` on WSL/Linux.

| Metric | Value | Notes |
|---|---|---|
| active engineering rules | 43 | unchanged (no new Rule kernels — rc53 is ADR + design-doc wave) |
| active governing principles | 13 | unchanged |
| gate rules | 143 | unchanged |
| self-test cases | 258 | unchanged |
| enforcer rows | 176 | unchanged |
| §4 constraints | 65 | unchanged |
| ADRs | 124 | rc53 +4 (ADR-0136..0139) on the rc51 baseline of 120 |
| Maven tests green | 453 | unchanged (Wave 6 is Javadoc-only) |
| architecture graph nodes | 598 | rc53 +4 (4 new ADR nodes) on the rc52 baseline of 594 |
| architecture graph edges | 1104 | rc53 +31 (authority + relates_to + supersedes_partial edges) on the rc52 baseline of 1073 |
| recurring defect families | 20 | rc53 +4 design-side families on the rc52 baseline of 16 |

## Competitive Pillars (Principle P-B / Rule R-B / ADR-0065)

rc53 status across the 4 pillars:

- **performance** — no regression (Wave 6 Javadoc-only; runtime path unchanged); Fast-Path / Slow-Path narrowed semantics (ADR-0139) preserve reactive + no-Thread.sleep invariants per Rule R-G + R-H.
- **cost** — no infrastructure cost change; ADR-0100 4-layer lifecycle preserved (no parallel-entity drift).
- **developer_onboarding** — improved: canonical L1 4+1 view documented for the first time at the module root; Vocabulary Glossary in Run / Task / Session / SuspendSignal / SuspendReason Javadoc eases new-developer onboarding; 5-layer L1 decomposition ratified for reference.
- **governance** — strengthened: 4 new defect families (design-side siblings of implementation invariants) registered; 4 red lines hard-codified in ADR-0138; per-wave G-A..G-F acceptance criteria piloted across 6 waves.

## Summary

Closes PR #71 (`docs/agent-service-l1-cn-20260525`) by performing a critical
re-review of its 5-layer L1 design, reconciling its academic vocabulary
with the platform's canonical Run≤Task≤Session≤Memory 4-layer lifecycle
hierarchy (ADR-0100), ratifying the 5-layer decomposition, and producing
the canonical L1 4+1 view as an interaction record under
`docs/logs/reviews/`. The original 8-wave plan collapses to 6 waves after
ADR-0100 reconciliation eliminates the would-be Run→Task / SuspendSignal→
InterruptSignal Java-level rename.

## Wave-by-Wave Deliverables

| Wave | Commit (short) | Deliverable | Lines |
|---|---|---|---|
| Wave 1 | `2759f3f` | Review draft + 4 ADR drafts + 4 new defect families | +1718 |
| Wave 2 | `cdbab35` | §14 Scenarios View + §15 Logical View | +421 |
| Wave 3 | `5732d94` | §16 Process View + §17 Physical View | +383 |
| Wave 4 | `e695b5e` | §18 Development View + §19 SPI Appendix + §20 L2 Boundary Contracts | +296 |
| Wave 5 | `04b2197` | 4 ADRs promoted to accepted + 3 deferred siblings closed | +99 |
| Wave 6 | `44f62bf` | 5 Java types Javadoc Vocabulary Glossary | +112 |
| Wave 7 | (deleted) | (no Run→Task / SuspendSignal rename per ADR-0100 reconciliation) | n/a |
| Wave 8 | (this commit) | ARCHITECTURE.md §0.5 pointer + historical-snapshot marker on review draft + this release note | TBD |

## ADRs Accepted (rc53)

- ADR-0136 — Vocabulary Reconciliation: PR 71 "Task" ≡ existing platform Task entity (not Run alias)
- ADR-0137 — SuspendSignal Canonical; InterruptSignal / InterruptReason are L1 Glossary Synonyms
- ADR-0138 — Agent Service 5-Layer L1 Ratification (PR 71 layers ↔ ADR-0100 components + Run≤Task≤Session≤Memory + 4 red lines)
- ADR-0139 — Fast-Path / Slow-Path Narrowed Semantics

## Defect-Family Ledger

**Extended (3 existing families)** with a rc53-wave-1 occurrence:
- F-l1-architecture-grounding-gap — PR #71 ER block missing tenantId + state diagram missing cancel re-auth/CAS
- F-cross-authority-agreement — PR #71 disagreements with bus-channels.yaml / s2c-callback.v1.yaml / contract-catalog.md
- F-terminal-verb-overclaim — PR #71 §3.4.1 cancel-state transitions present-tense for W2-deferred sub-clauses (re-opened from `closed` to `monitoring`)

**Registered (4 new families)** at rc53-wave-1:
- F-design-artifact-omits-tenant-spine — design ER blocks dropping tenantId (sibling of implementation-side F-nonatomic-run-status-write)
- F-design-doc-violates-three-track-bus — queue / bus abstractions conflating physical isolation with durability tier vs Rule R-E
- F-design-doc-language-bypasses-invariant — permissive design wording licensing R-G/R-H/R-J.a bypass
- F-placeholder-leaks-into-active-corpus — anonymous slugs (xiaoming / wanshoulu / foo / bar / TBD / TODO-template) leaking into stable URLs

`recurring_defect_families` count 16 → 20.

## 4 Red Lines (Carried Forward)

The L1 4+1 ratification (ADR-0138) hard-codes 4 invariants no downstream wave may breach:

1. **No tenantId-less data model.** Run / Task / Session / StateStore declare `tenantId` as first-class field. (Rule R-C.2.a + R-J.a + P-J)
2. **No cancel state machine without re-auth + atomic CAS.** Cancel re-validates `(tenantId == Run.tenantId)`; writes through `RunRepository.updateIfNotTerminal(...)` (Rule R-J.b + ADR-0118 + F-nonatomic-run-status-write defense — 4 prior recurrences).
3. **No single-tier internal queue with mode-based durability.** Queue split per Rule R-E into `control` / `data` / `rhythm` physical channels declared in `bus-channels.yaml`.
4. **No Fast-Path language implying skip of tenantId / RLS / reactive / SuspendSignal.** (ADR-0139 narrowed Fast-Path semantics; Rule R-G + R-H + R-J.a)

## Vocabulary Reconciliation (Glossary Synonyms — NOT renames)

Per ADR-0136 + ADR-0137, the academic vocabulary used in PR #71 and adjacent design docs is documented as glossary synonyms; **no Java code is renamed**:

| Academic / PR-71 spelling | Canonical platform spelling | Java type / SPI |
|---|---|---|
| Task (scheduling core) | Task (control-state) | `service.task.Task` (NOT a renamed Run) |
| TaskID | taskId | `Task.taskId` |
| InterruptSignal | SuspendSignal | `engine.orchestration.spi.SuspendSignal` |
| InterruptReason | SuspendReason | `service.runtime.resilience.spi.SuspendReason` |
| InterruptType (4 values) | (3-mechanism decomposition) | Task.A2aState + HookPoint + SuspendReason.AwaitClientCallback |
| Yield | HookPoint.ON_YIELD | `middleware.spi.HookPoint` |
| SessionManager | Session + ContextProjector | `service.session.Session` + `service.session.spi.ContextProjector` |
| ShadowToolInterceptor | ChatAdvisor + RuntimeMiddleware | `middleware.advisor.spi.ChatAdvisor` + `middleware.spi.RuntimeMiddleware` |
| ContextTranslator | ContextProjector + PromptTemplate + StructuredOutputConverter | 3-SPI composition |
| Engine Adapter Layer | EngineRegistry + ExecutorAdapter | `engine.spi.ExecutorAdapter` + registry per `engine-envelope.v1.yaml` |
| Internal Event Queue | three-track bus binding | `agent-bus` channels per `bus-channels.yaml` (control / data / rhythm) |

## Verification (WSL/Linux per Rule G-7)

```bash
# A. Gate self-check
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_parallel.sh 2>&1 | tee /tmp/gate-rc53-closure.log'

# B. Maven verify
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && ./mvnw -Pquality verify 2>&1 | tee /tmp/maven-rc53-closure.log'

# C. ADR YAML schemas (4 new ADRs)
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && for f in docs/adr/013{6,7,8,9}-*.yaml; do python3 -c "import yaml; yaml.safe_load(open(\"$f\"))" && echo "OK $f"; done'

# D. families.yaml content-diff (Rule G-9.b)
git diff HEAD~1 -- docs/governance/recurring-defect-families.yaml

# E. Architecture graph regeneration consistency
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && python3 gate/build_architecture_graph.py --check --no-write'
```

## Wave Closure Notes

- All 6 active waves passed G-A..G-F per-wave acceptance criteria (see each wave's Closure block in the canonical L1 4+1 review draft).
- The original 8-wave plan's Wave 7 (Run→Task hard rename + Flyway migration) was deleted after ADR-0100 reconciliation surfaced that Run, Task, Session, SuspendSignal are all canonical shipped types with distinct semantics.
- Branch `rc53/agent-service-l1-4plus1-rewrite` is the closure target; on merge to `main`, the rc53 baseline metrics update with the new ADR count (120 → 124) and the new family count (16 → 20).
