---
formal_release: true
evidence_bundle: gate/release-ci-evidence/2026-05-25-l0-rc41-final-release-readiness.evidence.yaml
release_candidate_commit: d20d1e395f85d022396fd848b885bba6ae429bd2
status: final-release-ready
---

# v2.0.0 - L0 final release readiness

> This file retains `rc41` in its filename only so the existing latest-release
> resolver, which orders release notes by `rcN`, can select it. The release
> decision below closes the RC state for the Layer-0 architecture baseline.

## Release Decision

- Decision: close RC state and publish the Layer-0 final release-ready architecture baseline.
- Frozen candidate commit: `d20d1e395f85d022396fd848b885bba6ae429bd2`
- Evidence bundle: `gate/release-ci-evidence/2026-05-25-l0-rc41-final-release-readiness.evidence.yaml`
- Formal release validator: `bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc41-final-release-readiness.evidence.yaml`

The final audit found no remaining L0 blocker, no unresolved contract-authority
conflict, and no current over-design that must be corrected before release. The
architecture is release-ready because the current shipped surface is explicitly
bounded, high-risk agentic capabilities are deferred behind promotion triggers,
and the canonical ledgers, gates, tests, ADR corpus, and contract catalog agree
at the frozen candidate commit.

## Generated Evidence

| Metric | Baseline | Live | Match |
|---|---:|---:|---|
| active_engineering_rules | 42 | 42 | true |
| active_gate_checks | 139 | 139 | true |
| gate_executable_test_cases | 249 | 249 | true |
| enforcer_rows | 172 | 172 | true |
| adr_count | 103 | 103 | true |
| maven_tests_green | 409 | 409 | true |
| architecture_graph_nodes | 475 | 475 | true |
| architecture_graph_edges | 852 | 852 | true |
| recurring_defect_families | 14 | 14 | true |

## Architecture Baseline

| Metric | Count | Evidence |
|---|---:|---|
| §4 constraints | 65 | `docs/governance/architecture-status.yaml` canonical baseline |
| ADRs | 103 | generated evidence baseline/live match |
| gate rules | 139 | generated evidence baseline/live match |
| gate self-test cases | 249 | `gate/test_architecture_sync_gate.sh` evidence run |
| active engineering rules | 42 | generated evidence baseline/live match |
| active governing principles | 13 | `docs/governance/architecture-status.yaml` canonical baseline |
| enforcer rows | 172 | generated evidence baseline/live match |
| maven_tests_green | 409 | Surefire/Failsafe XML report extraction after `./mvnw clean verify` |
| architecture_graph_nodes | 475 | current canonical graph baseline |
| architecture_graph_edges | 852 | current canonical graph baseline |
| recurring_defect_families | 14 | generated evidence baseline/live match |

## Current Agentic Architecture Decision

| Capability | Current release-ready contract | L0 decision |
|---|---|---|
| Execution engine | `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineRegistry`, `EngineEnvelope`, `SuspendSignal`, and service-owned `StatelessEngine` are the current shipped authority. | Accept as L0/L1 boundary. No additional runtime engine expansion is required for L0 release. |
| Dynamic planning | `plan-projection.v1.yaml` remains a design-only bridge; full planner DAG/tooling remains forward work. | Accept as intentionally deferred, not a blocker. |
| Skills | `SkillCapacityRegistry`, `SkillResolution`, and `ResilienceContract` keep capacity, resilience, and tenant-scoped skill resolution outside the engine internals. | Accept as sufficient L0 contract boundary. |
| Memory | `GraphMemoryRepository` and hook/context projection boundaries keep persistence outside direct engine ownership. | Accept as sufficient L0 memory boundary. |
| Knowledge | No current shipped knowledge subsystem is claimed. Knowledge remains W3+ deferred until ADR, schema, runtime, and gate evidence move together. | Accept as correct non-claim, not a missing L0 feature. |
| Sandbox and generated components | Dynamic Java compilation, `.apg` artifacts, debugger/mock registry, generated component packaging, and sandbox execution remain W2+ exploratory. | Accept as intentionally non-current; promotion requires dedicated contracts, security policy, tests, and gates. |

## Contract, Authority, and Constraint Closure

| Surface | Final check | Result |
|---|---|---|
| `CLAUDE.md` and `docs/governance/architecture-status.yaml` | Canonical rule, principle, gate, ADR, test, enforcer, graph, and recurring-family counts agree with generated evidence. | closed |
| `docs/contracts/contract-catalog.md` | Active SPI counts and agent-service SPI status distinguish interfaces from structural carriers. | closed |
| `docs/adr/` | The active rule corpus cites ADR authority, and deferred capabilities are not promoted without ADR backing. | closed |
| `agent-service/ARCHITECTURE.md` | L1 service architecture lists live package roots and 7 active Java SPI interfaces. | closed |
| `docs/logs/reviews/2026-05-25-agent-execution-engine-l1-high-level-design-proposal.en.md` | Current, accepted-forward, and exploratory tracks are separated for engine, planner, skills, memory, knowledge, sandbox, and generated components. | closed |
| `gate/check_architecture_sync.sh` | Rule 125 / E173 brings codegraph install truth into the same lockstep governance system as the rest of L0. | closed |

## Over-Design Assessment

The L0 baseline is not judged over-designed for release. The governance system
is intentionally heavy because the project has already seen repeated drift in
counts, authority surfaces, proposal scope, package truth, and release evidence.
That weight is now concentrated in gates and ledgers rather than runtime code.
The runtime contract remains small: execution, memory, skill capacity,
resilience, state, and projection are explicit boundaries; compiler, APG,
debugger, mock registry, full planner, and knowledge features are not shipped
claims.

The only future simplification recommendation is operational rather than
architectural: continue replacing manual architecture appendices with generated
or gate-checked views where the source of truth already exists in code,
contract catalogs, or governance ledgers.

## Four Competitive Pillars

- performance: no runtime hot-path change is introduced by final release
  closure; the release verifies existing engine, service, and governance
  contracts.
- cost: no new runtime infrastructure, storage service, planner service, or
  compiler service is added for L0 final release.
- developer_onboarding: the release note, corrected L1 proposal, service
  architecture, and contract catalog now separate current contracts from
  forward design without requiring readers to infer package truth.
- governance: canonical counts, evidence generation, recurring-family closure,
  and release validation now agree at the frozen candidate commit.

## Recurring Family Closure

| Family | Closure result | Residual risk |
|---|---|---|
| F-numeric-drift | closed for the final L0 baseline: 42 rules, 139 gate checks, 249 self-tests, 172 enforcer rows, 103 ADRs, 409 Maven tests, 475 graph nodes, 852 graph edges, and 14 recurring families. | Future baseline changes must regenerate evidence before release prose changes. |
| F-cross-authority-agreement | closed by current/forward/exploratory separation, proposal-overclaim gates, and package/signature truth checks. | New proposal docs must keep proposed names and shipped names separate. |
| F-l1-architecture-grounding-gap | closed for service L1 grounding and SPI/carrier separation. | Generated appendices should replace manual tables when the generator is ready. |
| F-project-tool-pin-drift | closed for the current codegraph onboarding wave by Rule 125 / E173 and canonical baseline refresh. | Future local tool additions must be wired into package truth, install truth, and gate evidence together. |

## Verification Commands

```bash
# WSL native temp clone, because /mnt/d resource copying can fail with host filesystem permissions.
./mvnw clean verify
./mvnw -Pquality -DskipTests verify

# Canonical release checks from the working tree.
bash gate/check_architecture_sync.sh
python3 gate/lib/build_release_evidence.py --run-self-tests --include-maven-reports --output gate/release-ci-evidence/2026-05-25-l0-rc41-final-release-readiness.evidence.yaml
bash gate/check_formal_release_transaction.sh --evidence gate/release-ci-evidence/2026-05-25-l0-rc41-final-release-readiness.evidence.yaml
```

## Residual Risk

No accepted residual L0 blocker remains. The remaining risks are forward
promotion risks only: dynamic compilation, generated component archives,
sandbox execution, debugger/mock registries, full dynamic planning, and
knowledge subsystems need their own ADRs, schemas, runtime implementations,
tests, and gates before becoming shipped claims.
