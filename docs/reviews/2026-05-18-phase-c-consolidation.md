---
level: L0
view: scenarios
affects_level: L0
affects_view: scenarios
affects_artefact:
  - ARCHITECTURE.md
  - agent-platform/ARCHITECTURE.md
  - agent-runtime/ARCHITECTURE.md
  - docs/governance/architecture-status.yaml
  - docs/governance/enforcers.yaml
  - docs/governance/principle-coverage.yaml
  - docs/governance/architecture-graph.yaml
freeze_id_lifted: W1-russell-2026-05-14
proposal_id: 2026-05-18-phase-c-consolidation
status: accepted
date: 2026-05-18
authority: ADR-0078 (agent-service consolidation, supersedes ADR-0055, extends ADR-0066)
---

# Phase C — agent-service consolidation (freeze lift)

## Proposal

Lift the `W1-russell-2026-05-14` freeze on `ARCHITECTURE.md` for the duration of
the Phase C consolidation wave (ADR-0078). The wave merges the prior
`agent-platform` + `agent-runtime` Maven modules into a single `agent-service`
module with sub-package layering
(`ascend.springai.service.platform.*` for the HTTP edge,
`ascend.springai.service.runtime.*` for the cognitive runtime kernel).

## Why a freeze lift is justified

The freeze stamp originated from the W1.x Phase 7 freeze activation
(2026-05-15) on the L1 corpus, anchored at the state where 9 reactor modules
were declared. ADR-0078 amends that decision: a single `agent-service`
module replaces the prior two compute_control modules. The amendment is
authorized by:

- L0 architecture decision (ARCHITECTURE.md §2 lines 75–86 as of the
  pre-Phase-C state) explicitly noting "Phase C follow-up will fold
  agent-platform + the runtime kernel into a single agent-service".
- User-confirmed Phase C decision matrix (Q1=A, Q2=A, Q3=A, Q4=A, Q5=B).
- ADR-0078 supersedes ADR-0055 and extends ADR-0066 (independent module
  evolution — one fewer compute_control module to evolve independently).

## Scope

This freeze lift applies ONLY to the artefacts named under `affects_artefact:`
above. All other freeze stamps remain in force.

## Verification

Six commits on branch `phase-c-merge` execute the move:
- Commit 1: agent-service skeleton + parent POM swap (build-green).
- Commit 2: git mv sources verbatim, delete orphan dirs.
- Commit 3: package rename `ascend.springai.{platform,runtime}` →
  `ascend.springai.service.{platform,runtime}`.
- Commit 4: governance + gate-script retargeting.
- Commit 5: documentation corpus retarget.
- Commit 6: spring-boot-maven-plugin classifier fix + verification.

Build-green verification (./mvnw clean verify on Linux semantics):
all 9 modules SUCCESS; BUILD SUCCESS.

Gate green verification (bash gate/check_parallel.sh): post-Phase-C re-run
expected to pass all 78 rules including the retargeted Rule 10
(module_dep_direction), Rule 28e (module_count_invariant: 9 → 8),
and Rule 68 (claude_md_kernel_matches_card) after CLAUDE.md regen from
the retargeted rule cards.
