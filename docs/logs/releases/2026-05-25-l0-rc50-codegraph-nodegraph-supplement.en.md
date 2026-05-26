---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml
nodegraph_evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-local.evidence.yaml
release_candidate_commit: b554d7445198e4c230631fbf59b14413017736f0
status: formal-release-ready
supplements: docs/logs/releases/2026-05-25-l0-rc49-agentic-contract-surface-corrective.en.md
responds_to: docs/logs/reviews/2026-05-25-l0-rc48-agentic-contract-surface-architecture-review.en.md
---

# v2.1.0-rc50 - CodeGraph Nodegraph Supplement Formal Release

> **Historical artifact frozen at SHA b554d7445198e4c230631fbf59b14413017736f0 (rc50 CodeGraph nodegraph supplement publication).** Baseline counts in this document (113 ADRs, 33 active SPI interfaces, 565 graph nodes, 1005 graph edges) reflect the rc50 publication baseline and are superseded at rc51 by the L0 Agentic-Completeness wave (120 ADRs, 38 active SPI interfaces, 587 graph nodes, 1065 graph edges). The rc50 baseline remains the canonical record for the rc43-rc49 primitive-tier scope plus the rc50 nodegraph-evidence supplement.

> This supplemental formal release note is valid only for frozen candidate
> commit `b554d7445198e4c230631fbf59b14413017736f0`, release evidence bundle
> `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml`,
> and local nodegraph evidence bundle
> `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-local.evidence.yaml`.

## Release Decision

- Decision: **ship** the rc50 supplemental publication for CodeGraph nodegraph evidence.
- Frozen commit: `b554d7445198e4c230631fbf59b14413017736f0`.
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml`.
- Nodegraph evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-local.evidence.yaml`.
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml`.
- Four-pillar coverage: performance, cost, developer_onboarding, governance.
- Active SPI interfaces: 33 total (19 pre-rc43 + 14 rc43).
- Architecture graph: 565 nodes / 1005 edges.
- Gate self-tests: 258/258 self-tests.

## Supplement Scope

rc49 closed the reviewed agentic contract surface findings. rc50 supplements
that release by including the local CodeGraph nodegraph artifact in the same
evidence trail without committing the regenerated SQLite database.

| Surface | Closure |
|---|---|
| Local CodeGraph nodegraph | `gate/lib/build_codegraph_nodegraph_evidence.py` reads `.codegraph/codegraph.db` through a temporary copy of the SQLite DB/WAL/SHM files and emits auditable YAML. |
| Clean-worktree truth | The nodegraph evidence builder now records `repository.dirty: false` for a clean git worktree and reserves `null` for unknown or unreadable git status. |
| Recurring defect family | `F-project-tool-pin-drift` now records rc50 as a second occurrence and documents why the local DB remains git-ignored while its shape is captured as evidence. |
| Release publication | This note binds the rc50 supplement to a frozen candidate commit, generated release evidence, and generated nodegraph evidence. |

## Canonical Baseline

| Metric | Count |
|---|---:|
| §4 constraints | 65 |
| ADRs | 113 |
| Active gate rules | 143 |
| Gate self-test cases | 258 |
| Active engineering rules | 43 |
| Enforcer rows | 176 |
| Architecture graph nodes | 565 |
| Architecture graph edges | 1005 |
| Recurring defect families | 15 |
| Maven tests green | 423 |

## Generated Release Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 43 | 43 | true |
| active_gate_checks | 143 | 143 | true |
| gate_executable_test_cases | 258 | 258 | true |
| enforcer_rows | 176 | 176 | true |
| adr_count | 113 | 113 | true |
| maven_tests_green | 423 | 423 | true |
| architecture_graph_nodes | 565 | 565 | true |
| architecture_graph_edges | 1005 | 1005 | true |
| recurring_defect_families | 15 | 15 | true |

## Generated Nodegraph Evidence

| Metric | Value |
|---|---:|
| repository dirty | false |
| DB tracked by git | false |
| DB size bytes | 10317824 |
| files | 427 |
| nodes | 3597 |
| edges | 6420 |
| unresolved refs | 860 |
| schema versions | 1, 4 |
| languages | java, python, yaml |

| Node kind | Count |
|---|---:|
| class | 177 |
| enum | 14 |
| enum_member | 60 |
| field | 249 |
| file | 294 |
| function | 166 |
| import | 1724 |
| interface | 38 |
| method | 799 |
| route | 4 |
| variable | 72 |

## Current-vs-Forward Claims

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| CodeGraph installation truth | `tools/codegraph/package.json`, `tools/codegraph/package-lock.json`, and `.mcp.json` are pinned and cross-platform-path checked. | Rule 125 / E173; `bash gate/check_architecture_sync.sh`. | Additional project-local MCP tools may reuse or generalize the pattern. | A second project-local MCP tool lands. | Its manifest, lockfile, shim path, and evidence surface exist. |
| Local nodegraph evidence | `.codegraph/codegraph.db` remains git-ignored, and its shape is captured in YAML evidence. | `gate/lib/build_codegraph_nodegraph_evidence.py`; local nodegraph evidence bundle. | CI may later regenerate nodegraph evidence from a native CodeGraph runtime. | CI environment can run the CodeGraph bundle natively. | The CI runtime can build the DB without local workstation state. |
| rc49 agentic corrective release | rc49 remains the corrective closure for the rc48 review findings. | rc49 formal release note and evidence bundle. | Runtime implementation waves continue behind the shipped contracts. | Per-capability W2-W4 implementation evidence. | Implementation evidence exists. |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| `F-project-tool-pin-drift` | rc40-codegraph-mcp-onboarding, rc50-nodegraph-evidence | `tools/codegraph/package.json`, `tools/codegraph/package-lock.json`, `.mcp.json`, `gate/lib/build_codegraph_nodegraph_evidence.py` | rc50 supplements install-truth gating with local nodegraph evidence | The evidence is generated from local `.codegraph` state until a native CI CodeGraph runtime is available. |
| `F-numeric-and-baseline-drift` | rc48 P0-1, P0-3, P1-1 | release notes, `architecture-status.yaml`, graph, release evidence | rc50 keeps the rc49 canonical baseline unchanged and generated from evidence | Future releases must regenerate evidence from the frozen candidate commit. |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `gate/lib/build_codegraph_nodegraph_evidence.py` | evidence tooling | Unit tests cover DB metric derivation and clean git status truth. |
| `gate/test_release_readiness_tools.py` | executable evidence | Six release-readiness tests passed under WSL. |
| `docs/governance/recurring-defect-families.yaml` | family ledger | rc50 occurrence added for CodeGraph nodegraph evidence. |
| `docs/governance/recurring-defect-families.md` | generated ledger view | Template idempotency check passed byte-identical. |
| `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml` | workflow evidence | Generated from clean WSL-driven clone at candidate commit. |
| `gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-local.evidence.yaml` | nodegraph evidence | Generated from the local git-ignored CodeGraph DB. |

## Verification Commands

All commands were driven from WSL.

```bash
python3 -m unittest gate.test_release_readiness_tools
python3 gate/lib/check_template_render_idempotency.py --verbose
git diff --check
./mvnw clean verify
./mvnw -Pquality -DskipTests verify
bash gate/test_architecture_sync_gate.sh
bash gate/check_architecture_sync.sh
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml
python3 gate/lib/build_codegraph_nodegraph_evidence.py --root . --output gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-local.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc50-codegraph-nodegraph-supplement.evidence.yaml
```

## Residual Risk

No accepted residual blocks this supplemental release. The local CodeGraph DB
is intentionally not committed; the evidence records its shape for this
workstation-backed release. A future CI-native nodegraph regeneration can
promote the evidence from local supplemental artifact to reproducible CI
artifact once the CodeGraph runtime is available in that environment.
