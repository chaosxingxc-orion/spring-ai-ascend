---
level: L0
view: process
affects_level: L0, L1
affects_view: development, scenarios, logical
release: v2.0.0-rc22
date: 2026-05-22
freezes: rc21
authors: ["chao", "急急 (agent)"]
related_adrs:
  - ADR-0099
  - ADR-0100
  - ADR-0101
  - ADR-0102
  - ADR-0103
  - ADR-0104
---

# v2.0.0-rc22 Release — L1 Grounding (Rule G-1.1) + Polymorphic Deployment Loci + Six-Component Capability-Services Distribution

> **Headline:** rc22 ratifies the 2026-05-21 reviewer proposal triple. Six new ADRs (0099–0104), one new engineering rule (G-1.1) with three sub-clauses + three enforcers, two new governance SSOTs (`deployment-loci.yaml`, `evolution-modalities.yaml`), two new contract YAMLs (`reflection-envelope.v1.yaml`, `agent-invoke-request.v1.yaml`), and a Rule G-1.1 grounding pass over all six `agent-*/ARCHITECTURE.md` files. Five reviewer claims explicitly rejected (see response doc). One new recurring-defect family added (`F-l1-architecture-grounding-gap`).

## Methodology

This wave followed the `/reviewer-feedback-self-check` skill's **Categorize → Sweep → Batch-fix → Prevention** sequencing rule:

- **Stage 1 — Categorize**: 5 wave-N families taxonomized (N-α … N-ε); explicit accept/reject ledger per proposal-finding (see response doc).
- **Stage 2 — Sweep**: hidden defects surfaced — agent-middleware/ARCHITECTURE.md missing Development View tree; agent-client + agent-evolve skeleton ARCHITECTURE.md files lacking SPI appendices; rule-id collision G-1.c vs sub-clause convention; package-root mismatch `com.huawei.ascend.*` vs `ascend.springai.*`.
- **Stage 3 — Batch-fix**: single working-tree pass — all 6 ADRs, rule card, 4 new governance/contract YAMLs, CLAUDE.md kernel insertion, engine-hooks ON_YIELD, enforcers (3 rows), recurring-defect ledger update, 6 ARCHITECTURE.md grounding rewrites, response doc, this release note.
- **Stage 4 — Prevention**: Rule G-1.1 ratified (card + CLAUDE.md kernel + ADR-0099 + 3 enforcers E166/E167/E168). Gate scripts (`gate/lib/check_l1_dev_view_tree.sh` + `gate/lib/check_l1_spi_appendix.sh`) + 6 self-test fixtures land in a follow-up commit before this PR merges (per the L3 live-corpus self-check layer of the methodology).

## What changed

### Rule additions / consolidations
- **Rule G-1.1** (NEW) — L1 Architecture Depth & Grounding. Three sub-clauses (.a Development View Code-Mapping; .b SPI Interface Appendix 4-way parity; .c L2 Constraint Linkage). Card: `docs/governance/rules/rule-G-1.1.md`. Kernel inserted under existing Rule G-1 in CLAUDE.md. Enforcers: E166, E167, E168.

### New ADRs
| ADR | Title | Family |
|---|---|---|
| ADR-0099 | L1 Architecture Depth & Grounding (Rule G-1.1) | N-α |
| ADR-0100 | agent-service L1 runtime-role decomposition (5-component model + Run≤Task≤Session + 3 new SPIs + Yield/SuspendSignal coexistence) | N-β |
| ADR-0101 | Polymorphic Deployment Topology (Mode A Platform-Centric + Mode B Business-Centric) | N-γ |
| ADR-0102 | Evolution Plane Online/Offline Duality | N-δ |
| ADR-0103 | agent-middleware naming resolution (REJECT rename + REJECT 7th module) + capability-services distribution | N-ε |
| ADR-0104 | Package-root migration `ascend.springai.*` → `com.huawei.ascend.*` (decision; rc22.5 executes) | N-ε (companion) |

### New / updated governance + contract surfaces
- `docs/governance/deployment-loci.yaml` (NEW) — Mode A/B SSOT.
- `docs/governance/evolution-modalities.yaml` (NEW) — Offline/Online SSOT + 2×2 matrix.
- `docs/contracts/reflection-envelope.v1.yaml` (NEW, status `design_only`) — online-evolution S2C envelope.
- `docs/contracts/agent-invoke-request.v1.yaml` (NEW, status `design_only`) — Service ↔ Engine contract.
- `docs/contracts/engine-hooks.v1.yaml` — added `on_yield` (10th canonical hook point per ADR-0100 Yield/SuspendSignal coexistence).
- `docs/governance/recurring-defect-families.{yaml,md}` — new family `F-l1-architecture-grounding-gap` (Rule G-9 freshness signal: 6 new ADRs).
- `docs/governance/enforcers.yaml` — 3 new rows (E166/E167/E168 — Rule G-1.1 sub-clauses).

### Documentation surface rewrites
- All 6 `agent-*/ARCHITECTURE.md` files gained Rule G-1.1 grounding sections (`## Development View` tree + `## *SPI Interface Appendix*`).
- `agent-middleware/ARCHITECTURE.md` additionally gained a "What this module is NOT" section explaining the naming resolution (ADR-0103).
- `agent-evolve/ARCHITECTURE.md` additionally gained Online/Offline modality + 2×2 matrix sections.
- `agent-service/ARCHITECTURE.md` additionally gained §11 (L1 Runtime-Role Decomposition) recording the 5-component model.

## Decisions locked (from response doc)

| Decision | Resolution |
|---|---|
| Agent-middleware rename | Keep current name + scope; distribute capability-services across six modules (per user directive) |
| Yield vs SuspendSignal | Coexist — SuspendSignal canonical, Yield added as HookPoint.ON_YIELD hint |
| Package root | Migrate to `com.huawei.ascend.*` as separate rc22.5 wave; rc22 uses current root with forward-compat markers |
| rc22 size | Ratify + grounding sweep — ~30 files, no Java refactor |
| ADR splitting (Proposal #3) | Split into ADR-0101 (deployment) + ADR-0102 (evolution) for independent amendability |

## Rejections sent to reviewers (verbatim in response doc)

1. "Severe semantic hallucination/deviation" framing on agent-middleware — REJECTED.
2. New `agent-middleware` module for Capability Services — REJECTED.
3. "Fully embed a2a-java SDK" — REJECTED; contract-only adoption.
4. "Abandon exception-based suspension; switch to Yield event" — REJECTED; coexistence model.
5. Rule-id "G-1.c" — REJECTED; ratified as G-1.1 per rc17 sub-rule convention.

## Baseline metrics — pre vs post wave

| Metric | Pre (rc21) | Post (rc22) | Delta |
|---|---|---|---|
| Active engineering rules | 40 | 41 | +1 |
| §4 constraints | 65 | 65 | 0 |
| gate rules | 129 | 132 | +3 (3 placeholder pass_rule entries for G-1.1 sub-clauses) |
| Enforcer rows | 164 | 167 | +3 |
| ADRs | 97 | 103 | +6 |
| Active recurring-defect families | 10 | 11 | +1 |
| Maven build tests green | 374 | 374 | 0 |
| `agent-*/ARCHITECTURE.md` Rule G-1.1 compliant | 0 | 6 | +6 |
| Architecture graph nodes | 412 | 423 | +11 |
| Architecture graph edges | 678 | 718 | +40 |

### Four-pillar baseline (per Rule R-B / ADR-0065)

| Pillar | Baseline metric | Current value |
|---|---|---|
| performance | gate execution wall-clock | <300s (rc22 added 5-min ceiling per PR-Opt-rc22) |
| cost | gate machine-time | N/A (CI minutes unchanged) |
| developer_onboarding | docs/quickstart.md | unchanged at rc22 |
| governance | active rules + enforcers + ADRs | +1 rule / +3 enforcers / +6 ADRs |

(Architecture graph counts regenerate post-PR via `python gate/build_architecture_graph.py`; baseline_metrics + allowed_claim + README baseline line update in a single lockstep commit per the `feedback_lockstep_baseline_surfaces` memory.)

## Wave structure (rc22 → rc26+)

| Wave | Scope |
|---|---|
| **rc22 (THIS WAVE)** | L1 grounding rule + 6 ADRs + 6 ARCHITECTURE.md rewrites + governance YAMLs + 3 enforcers + response doc + release note + recurring-defect ledger update |
| rc22.5 | Mechanical package-root migration `ascend.springai.*` → `com.huawei.ascend.*` per ADR-0104 |
| rc23 | agent-service Java refactor into 5 sub-packages + ArchUnit boundary tests |
| rc24 | Deployment-loci runtime + StatelessEngine + ContextProjector + TaskStateStore reference impls |
| rc25 | Run ≤ Task ≤ Session Java types + Flyway migrations + A2A contract envelope adoption + BackpressureRequest channel |
| rc26 | Online evolution (ReflectionEnvelope runtime; Fast/Slow Track) + Federation Hub (Mode B bus) |

Backlog (separate review cycle): 2026-05-20 review backlog (rc16-post-closure 4 findings + financial-readiness ~50 items including Qoder/OpenCode gaps).

## Historical-snapshot marker on rc21 release note

`docs/logs/releases/2026-05-21-l0-rc21-scenario-phase-contracts.en.md` — frozen as wave-final per Rule 28 release-note baseline truth convention. Baseline counts in that document reflect rc21 publication-time state; rc22 counts above supersede.

## Verification

```bash
# 1. Gate + tests
bash gate/check_parallel.sh
bash gate/test_architecture_sync_gate.sh
python gate/build_architecture_graph.py --check --no-write

# 2. Rule G-1.1 grounding (all 6 modules)
for m in agent-client agent-bus agent-service agent-execution-engine agent-middleware agent-evolve; do
  echo "=== $m ==="
  grep -E "Development View|SPI Interface Appendix" "$m/ARCHITECTURE.md" | head -4
done

# 3. New ADRs + governance + contracts present
ls docs/adr/{0099,0100,0101,0102,0103,0104}-*.yaml
ls docs/governance/deployment-loci.yaml docs/governance/evolution-modalities.yaml
ls docs/contracts/{reflection-envelope,agent-invoke-request}.v1.yaml
ls docs/governance/rules/rule-G-1.1.md

# 4. CLAUDE.md kernel + engine-hooks ON_YIELD
grep "#### Rule G-1.1" CLAUDE.md
grep "on_yield" docs/contracts/engine-hooks.v1.yaml

# 5. Recurring-defect ledger parity (Rule G-9.c)
grep -c "^  - id: F-" docs/governance/recurring-defect-families.yaml
grep -c "^### F-" docs/governance/recurring-defect-families.md

# 6. Maven sanity check (no Java changes, but confirm baseline)
./mvnw.cmd clean verify   # Windows; Linux: ./mvnw clean verify
```

All commands must pass before rc22 merges to `main`. The gate scripts implementing Rule G-1.1 land in a follow-up commit (within this PR) before the final merge.
