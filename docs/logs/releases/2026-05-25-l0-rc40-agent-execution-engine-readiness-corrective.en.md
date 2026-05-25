---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml
release_candidate_commit: 64c1d190a519e718b67acab052430ed7571212f8
status: formal-release-candidate
---

# v2.0.0-rc40 - agent execution engine readiness corrective

> **Historical artifact frozen at SHA 64c1d190a519e718b67acab052430ed7571212f8 (rc40 agent-execution-engine-readiness corrective).** Baseline counts in this document (138 active gate rules / 245 self-tests / 171 enforcer rows / 474 graph nodes / 850 graph edges / 13 recurring defect families) reflect the corpus state at rc40 release time and are NOT retroactively updated. The current canonical baseline (after the rc41 codegraph_install_truth wave: 139 / 249 / 172 / 475 / 852 / 14) is tracked in `docs/governance/architecture-status.yaml.architecture_sync_gate.allowed_claim` and `README.md`.

## Release Decision

- Decision: ship corrective response
- Frozen commit: `64c1d190a519e718b67acab052430ed7571212f8`
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml`
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml`

rc40 closes the May 25 agent-execution-engine readiness review by correcting
proposal scope, package truth, capability boundaries, L1 architecture grounding,
and proposal-overclaim prevention gates. The release is a corrective response:
it publishes the corrected design authority and prevention gates, not a new
runtime engine implementation claim.

## Generated Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 42 | 42 | true |
| active_gate_checks | 138 | 138 | true |
| gate_executable_test_cases | 245 | 245 | true |
| enforcer_rows | 171 | 171 | true |
| adr_count | 103 | 103 | true |
| maven_tests_green | 409 | 409 | true |
| architecture_graph_nodes | 474 | 474 | true |
| architecture_graph_edges | 850 | 850 | true |
| recurring_defect_families | 13 | 13 | true |

## Architecture Baseline

| Metric | Count | Evidence |
|---|---:|---|
| §4 constraints | 65 | `architecture-status.yaml` canonical baseline |
| ADRs | 103 | generated evidence baseline/live match |
| gate rules | 138 | generated evidence baseline/live match |
| Gate self-test cases | 245 | `gate/test_architecture_sync_gate.sh` evidence run |
| active engineering rules | 42 | generated evidence baseline/live match |
| active governing principles | 13 | `architecture-status.yaml` canonical baseline |
| enforcer rows | 171 | generated evidence baseline/live match |
| maven_tests_green | 409 | Surefire/Failsafe XML report extraction |
| architecture_graph_nodes | 474 | `bash gate/build_architecture_graph.sh` in clean WSL evidence clone |
| architecture_graph_edges | 850 | `bash gate/build_architecture_graph.sh` in clean WSL evidence clone |
| recurring_defect_families | 13 | generated evidence baseline/live match |

## Fixes Completed

1. The rc39 formal release transaction authority was merged into this response branch before new proposal work proceeded.
2. The agent execution engine L1 proposal now separates current L0/L1 behavior, accepted-forward ADR-0112 direction, and W2+ exploratory work.
3. Non-current engine package and executor names are marked proposed/not-current where retained as historical proposal text.
4. Dynamic compilation, generated component packaging, `.apg` artifacts, debugger/mock registry, and sandbox/knowledge/memory expansions are explicitly W2+ exploratory until boundary contracts and tests land.
5. `agent-service/ARCHITECTURE.md` now maps the live service package tree and separates 7 active Java SPI interfaces from SPI-adjacent structural carriers.
6. Rules 122, 123, and 124 plus E170-E172 prevent immediate-scope proposal overclaims, contradictory package/signature claims, and unsupported absolute security/performance claims.
7. Gate and README baselines were regenerated for 138 active gate rules, 245 self-tests, 171 enforcer rows, and a 474-node / 850-edge architecture graph.

## Current-vs-Forward Claims

| Subject | Current shipped behavior | Verified by | Forward behavior | Promotion trigger | Must not claim before |
|---|---|---|---|---|---|
| Agent execution engine proposal scope | Current code authority is the existing L0/L1 engine/service boundary; the rc40 proposal is a corrected design draft, not immediate runtime delivery. | Corrected proposal, Rule 122, `bash gate/check_parallel.sh` | W2+ execution-engine expansion may introduce new contracts and generated components. | Boundary contracts, ADR updates, implementation tests, and gate rows land together. | Immediate W0/W1 execution of pending boundary-contract work. |
| Engine package/signature truth | Current authority uses `com.huawei.ascend.engine.*` plus service-owned `com.huawei.ascend.service.engine.spi.StatelessEngine`; `StatelessEngineExecutor` is not current. | Rule 123, corrected proposal records, `agent-service/ARCHITECTURE.md` | Future proposal names may be introduced as explicit proposed/future artifacts. | New Java source, ADR, contract catalog, and architecture status rows agree. | `com.huawei.ascend.agent.engine` or `StatelessEngineExecutor` is current shipped authority. |
| Dynamic compiler and APG artifacts | No dynamic compiler, generated component packager, `.apg` runtime artifact, debugger registry, or mock registry is shipped in L0/L1. | Corrected proposal capability boundary matrix | W2+ may design these as separate contracts with provenance, sandbox, and test policy. | Schema, security policy, artifact lifecycle, and verification gates are accepted. | Dynamic compilation or APG packaging is production-ready. |
| Security/performance absolutes | No proposal may claim bulletproof safety, zero-day safety, zero downtime, or sub-millisecond behavior without same-line evidence/deferred framing. | Rule 124 and self-tests | Benchmarked or threat-modeled claims may be promoted later. | Benchmark/threat-model evidence is published with acceptance criteria. | Absolute safety/performance claims are release facts. |
| L1 service architecture grounding | Service architecture lists live package directories and counts 7 active Java SPI interfaces separately from structural carriers. | `agent-service/ARCHITECTURE.md`, Rule G-1.1, `bash gate/check_parallel.sh` | A generated SPI appendix may replace manual tables later. | Generator and parity gate land together. | Structural carriers are active SPI interfaces. |

## Recurring Family Closure

| Family | Cited findings | Sibling surfaces checked | Closure result | Residual risk |
|---|---|---|---|---|
| F-numeric-drift | P0-1, P2-1, P2-2 | `architecture-status.yaml`, `gate/README.md`, `README.md`, enforcer rows, generated rule corpus, evidence bundle | closed for rc40 counts | Future gate/enforcer additions must regenerate evidence before release prose. |
| F-cross-authority-agreement | P1-1, P1-2, P1-3, P1-6, P2-1, P2-2 | Proposal docs, package names, ADR-0112 current/forward boundary, capability matrix, old proposal records | structurally addressed by Rules 122-124 | New proposal surfaces must use explicit proposed/future wording until implemented. |
| F-l1-architecture-grounding-gap | P1-7 | `agent-service/ARCHITECTURE.md` development tree and SPI appendix | closed for the service document; family remains monitoring | Manual architecture appendices can drift again until generated from code/catalog sources. |

## Authority Refresh

| Surface | Role | Freshness proof |
|---|---|---|
| `docs/logs/reviews/2026-05-25-agent-execution-engine-l1-high-level-design-proposal.en.md` | normative corrective proposal | Rewritten to current/forward/exploratory structure. |
| `agent-service/ARCHITECTURE.md` | L1 architecture authority | Development tree and SPI appendix refreshed against live code. |
| `gate/check_architecture_sync.sh` | canonical gate | Rules 122-124 added and extracted to `gate/rules/`. |
| `gate/test_architecture_sync_gate.sh` | executable prevention evidence | 245/245 self-tests passed at rc40 snapshot, including six new proposal-overclaim fixtures. |
| `docs/governance/enforcers.yaml` | enforcer ledger | E170-E172 added for Rules 122-124. |
| `docs/governance/recurring-defect-families.yaml` | recurring-family ledger | rc40 occurrences recorded for numeric drift, cross-authority disagreement, and L1 grounding. |
| `docs/governance/architecture-status.yaml` | canonical baseline | Baselines updated from live WSL evidence. |
| `gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml` | generated evidence | Generated from clean WSL clone of frozen commit `64c1d190a519e718b67acab052430ed7571212f8`. |

## Four Competitive Pillars

- performance: no runtime hot-path change; verification was driven from WSL and the gate remains parallel.
- cost: no new runtime infrastructure; added controls are release/gate-time checks.
- developer_onboarding: proposal readers now see current vs future boundaries without reverse-engineering Java package truth.
- governance: formal evidence, recurring-family updates, and proposal-specific prevention gates now move together.

## Verification Commands

```bash
# Executed from WSL. Maven full verify was run in a WSL-native temp workspace to avoid /mnt/d file-resource failures.
./mvnw clean verify
./mvnw -Pquality -DskipTests verify
bash gate/test_architecture_sync_gate.sh
bash gate/check_parallel.sh
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc40-agent-execution-engine-readiness-corrective.evidence.yaml
```

## Residual Risk

No accepted residual blocks this corrective publication. The remaining risks are
promotion risks: future dynamic compiler/APG/sandbox/debugger capabilities need
their own ADRs, schemas, tests, and gate rows before they can become shipped
claims. Local nodegraph/codegraph artifacts are intentionally out of scope for
this corrective release and are not included in the frozen candidate commit.
