---
affects_level: L0
affects_view: process
proposal_status: review
authors: ["Codex architecture review"]
responds_to: docs/releases/2026-05-18-l0-rc7-corrective.en.md
related_adrs:
  - ADR-0034
  - ADR-0051
  - ADR-0078
  - ADR-0079
  - ADR-0080
  - ADR-0081
related_rules:
  - Rule 31
  - Rule 32
  - Rule 33
  - Rule 34
  - Rule 54
  - Rule 82
  - Rule 84
  - Rule 86
  - Rule 87
affects_artefact:
  - ARCHITECTURE.md
  - agent-runtime-core/ARCHITECTURE.md
  - agent-service/module-metadata.yaml
  - docs/dfx/spring-ai-ascend-graphmemory-starter.yaml
  - docs/governance/architecture-status.yaml
  - docs/releases/2026-05-18-l0-rc7-corrective.en.md
  - docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml
  - gate/check_parallel.sh
  - gate/check_architecture_sync.sh
  - gate/test_architecture_sync_gate.sh
---

# L0 rc7 Post-Corrective Architecture Review

## Executive verdict

Do **not** publish a no-findings L0 completion release note yet.

The core agent runtime architecture is still directionally sound: the separation
between `agent-runtime-core`, `agent-service`, `agent-execution-engine`,
`agent-middleware`, the checked S2C suspension path, dynamic execution modes,
skill-capacity arbitration, and the memory/knowledge boundary is not over-designed
for L0. The remaining problems are narrower but still L0-relevant: the latest
rc7 corrective wave fixed the previous six findings, but introduced or left
behind three authority/verification gaps in the memory SPI and gate surfaces.

The user's prompt still names the rc6 release note. Current repository truth is
newer: HEAD is `8fb6b21 docs(rc7-response): close 6 rc6 post-response review
findings...`, and `docs/releases/2026-05-18-l0-rc6-post-response.en.md` is now
explicitly marked as a historical artifact superseded by
`docs/releases/2026-05-18-l0-rc7-corrective.en.md`. This review therefore uses
the rc7 corrective state as the current architecture baseline.

## Verification performed

| Command | Result |
|---|---|
| `python gate/build_architecture_graph.py` | PASS. Wrote 341 nodes / 474 edges; graph validation OK. |
| `bash gate/check_architecture_sync.sh` | PASS. Serial canonical gate includes Rule 86 and Rule 87. |
| `bash gate/check_parallel.sh` | Exit 0, but **coverage gap found**: output contains no `root_architecture_count_and_path_truth` or `status_yaml_allowed_claim_module_name_truth` lines. The parallel wrapper is not executing the two rc7 rules. |
| `bash gate/test_architecture_sync_gate.sh` | Exit 0, but the summary numerator is lower than the hard-coded denominator. This contradicts the rc7 release note's self-test claim and should fail the script. |
| `.\mvnw.cmd clean verify` | PASS. Full 10-project Maven reactor succeeds; Java/runtime tests are green. |

## Findings

### P0-1 - Memory SPI ownership is inconsistent after rc7

The rc7 wave says it rewrote root `ARCHITECTURE.md` for current module topology,
but the new root tree places `GraphMemoryRepository` under `agent-runtime-core`
while the real code and several canonical module artefacts still place it under
`agent-service`.

Evidence:

| Artifact | Evidence |
|---|---|
| `ARCHITECTURE.md:163-169` | The root tree shows `agent-runtime-core/src/main/java/ascend/springai/service/runtime/memory/spi/` and labels it as `GraphMemoryRepository`. |
| Actual source tree | `agent-runtime-core/src/main/java/ascend/springai/service/runtime/memory/spi/GraphMemoryRepository.java` does not exist. The real file is `agent-service/src/main/java/ascend/springai/service/runtime/memory/spi/GraphMemoryRepository.java`. |
| `docs/contracts/contract-catalog.md:30` and `:43` | Contract catalog says `GraphMemoryRepository` belongs to `agent-service`; `agent-service` has two SPI interfaces: `GraphMemoryRepository` and `ResilienceContract`. |
| `agent-service/module-metadata.yaml:13-15` | `agent-service` declares `ascend.springai.service.runtime.memory.spi`. |
| `agent-runtime-core/module-metadata.yaml:13-16` | `agent-runtime-core` declares orchestration, runs, and S2C SPI packages only; no memory SPI package. |
| `docs/governance/architecture-status.yaml:1001` | The implementation list also points to the `agent-service` GraphMemoryRepository path. |
| `docs/governance/architecture-status.yaml:1410` | The allowed claim says `agent-runtime-core` declares SPI packages for orchestration / runs / S2C / memory, contradicting module metadata and the actual source tree. |
| `docs/releases/2026-05-18-l0-rc7-corrective.en.md:49` | The release note repeats the same current-tense claim that root architecture now shows `agent-runtime-core` with `memory.spi`. |
| `docs/dfx/spring-ai-ascend-graphmemory-starter.yaml:32` | Still says the `GraphMemoryRepository` SPI is in `agent-runtime/memory/spi`, an obsolete pre-ADR-0078 path. |

Impact:

This directly affects the memory/knowledge architecture the user asked us to
review. L0 cannot simultaneously teach that memory SPI belongs to
`agent-runtime-core`, `agent-service`, and historical `agent-runtime`. The
runtime design can support either placement, but the authoritative corpus must
choose one.

Recommendation:

Choose one of these two paths explicitly:

1. **Documentation-only correction, lowest risk:** keep the Java source where it
   is today under `agent-service`, then update root `ARCHITECTURE.md`,
   `architecture-status.yaml:1410`, the rc7 release note, and the graphmemory
   starter DFX file to state that `GraphMemoryRepository` remains an
   `agent-service` SPI until a future ADR moves it.
2. **Architectural move, higher risk:** move `GraphMemoryRepository` into
   `agent-runtime-core`, then update `agent-runtime-core/module-metadata.yaml`,
   `docs/dfx/agent-runtime-core.yaml`, `contract-catalog.md`,
   `agent-service/module-metadata.yaml`, starter dependencies, imports, and
   tests. This should be done only if ADR-0079 intentionally meant to hoist
   memory SPI into the shared kernel.

Whichever path is chosen, extend Rule 86 so it validates path claims inside the
root architecture tree code block. The current Rule 86 excludes fenced code
blocks, which is why this tree-level ownership drift escaped.

### P0-2 - Parallel gate silently omits Rule 86 and Rule 87

`bash gate/check_parallel.sh` exits 0 but does not execute the two new rc7 rules.

Evidence:

| Artifact | Evidence |
|---|---|
| `gate/check_architecture_sync.sh:4038` | `# Summary` appears before the rc7 prevention wave. |
| `gate/check_architecture_sync.sh:4049` and `:4130` | Rule 86 and Rule 87 are declared after the `# Summary` marker. |
| `gate/check_parallel.sh:100-125` | The parallel wrapper splits the canonical script into rule ranges and stops at `^# Summary$`. |
| Fresh `bash gate/check_parallel.sh` run | Exit code is 0 and the trailer says `GATE: PASS`, but neither `root_architecture_count_and_path_truth` nor `status_yaml_allowed_claim_module_name_truth` appears in stdout. |
| `docs/releases/2026-05-18-l0-rc7-corrective.en.md` | Claims `bash gate/check_parallel.sh` is a pass for the rc7 surface. That is incomplete because two new rules are skipped. |

Impact:

This recreates the same defect family rc7 was supposed to close: a gate entrypoint
claims synchronization while not exercising the newest prevention rules. Any CI
or reviewer relying on `check_parallel.sh` can miss root architecture count/path
drift and status-ledger module-name drift.

Recommendation:

- Move Rule 86 and Rule 87 above the `# Summary` marker, or update
  `check_parallel.sh` so it treats a later rule header as authoritative even if
  an earlier summary comment exists.
- Add a parity self-test: the set of rule slugs executed by `check_parallel.sh`
  must equal the set of rule slugs executed by `check_architecture_sync.sh`.
- Make the parallel trailer include the executed rule count so omissions are
  visible without manually scanning stdout.

### P1-1 - Gate self-test summary is not trustworthy enough for release evidence

`bash gate/test_architecture_sync_gate.sh` exits 0, but its displayed summary does
not match the hard-coded total expected by the script and release note.

Evidence:

| Artifact | Evidence |
|---|---|
| `gate/test_architecture_sync_gate.sh:4098` | Hard-codes `TOTAL=143`. |
| `gate/test_architecture_sync_gate.sh:4163-4168` | Exits 1 only when `failed > 0`; it does not fail when the pass count is lower than `TOTAL`. |
| Fresh run | Exit code is 0, but the summary displays a lower numerator than denominator. |
| `docs/releases/2026-05-18-l0-rc7-corrective.en.md` | Claims a clean full self-test total for rc7. |

Impact:

This is a verification-truth issue. The individual cases appear to print PASS,
but the harness's own summary is internally inconsistent and does not fail closed.
For an L0 architecture gate, the self-test harness should never report a partial
summary with exit 0.

Recommendation:

- Fail the self-test when `passed != TOTAL`, even if no individual `FAIL` lines
  are present.
- Derive `TOTAL` from actual expected cases or from a maintained manifest, not
  from a second hard-coded integer.
- Fix aggregation so batch result concatenation cannot undercount PASS lines.
  A simple guard is to ensure every batch result file ends with a newline before
  concatenation and to count from the final sorted result stream.
- Re-run and update all rc7 evidence lines after the harness prints a consistent
  full total.

### P1-2 - Module-level authority text still lags ADR-0079

Most rc7 module-boundary fixes landed, but a few active module documents still
describe pre-ADR-0079 ownership or under-enumerate current shared-core contents.

Evidence:

| Artifact | Evidence |
|---|---|
| `agent-service/module-metadata.yaml:10` | Says `service.runtime` owns orchestration SPI and Run lifecycle. Post-ADR-0079, `agent-runtime-core` owns orchestration SPI and Run lifecycle; `agent-service` owns reference adapters, HTTP edge, memory SPI, and resilience SPI. |
| `agent-runtime-core/ARCHITECTURE.md:30-33` | Lists `runs/RunRepository.java`, but the real path is `runs/spi/RunRepository.java`; omits S2C SPI, `TraceContext`, and `ExecutorDefinition` from the contents list even though module metadata and root architecture say they are shared-core surfaces. |

Impact:

This is lower risk than the memory SPI conflict because the main gates and
contract catalog largely encode the correct runtime split. It still matters for
L0 onboarding: module metadata and per-module architecture docs are authoritative
under Rules 31 and 33.

Recommendation:

- Rewrite `agent-service/module-metadata.yaml#description` to say
  `service.runtime` owns posture-gated reference adapters plus memory/resilience
  SPI surfaces, while `agent-runtime-core` owns orchestration/run/S2C kernel
  contracts.
- Refresh `agent-runtime-core/ARCHITECTURE.md` contents to include
  `runs/spi/RunRepository.java`, S2C SPI, `TraceContext`, and
  `ExecutorDefinition`.
- Extend Rule 84 or add a module-metadata description truth check for ownership
  phrases such as "owns orchestration SPI" after ADR-0079.

### P2-1 - ADR-0081 verification text is corrupt/stale

Evidence:

| Artifact | Evidence |
|---|---|
| `docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml` | The verification section contains a malformed self-test evidence line with mojibake and an obsolete denominator. |
| Fresh run | The current self-test harness does not print the value recorded in ADR-0081; it prints a different inconsistent summary. |

Impact:

This does not change the architecture decision. ADR-0081's dual-surface design is
sound, but the verification evidence line should not be left corrupt in an
accepted ADR.

Recommendation:

After fixing P1-1, rewrite ADR-0081's verification line with the exact observed
self-test output.

## Agent architecture and overdesign assessment

No substantive overdesign issue was found in the runtime architecture.

| Area | Assessment |
|---|---|
| Dynamic planning | The current PlanProjection posture remains appropriate for L0: scheduler-admission projection is separated from the full W4 planner. No premature planner implementation was found. |
| Skills and capacity | Rule 41 / ADR-0070 / ADR-0081 now correctly separate operation-policy routing from skill-capacity arbitration. This is a useful L0 abstraction, not overdesign. |
| Memory and knowledge | The ownership principle is sound: `GraphMemoryRepository` is a platform/delegated memory SPI, not the default owner of customer business ontology. The remaining issue is module ownership truth, not the memory abstraction. |
| Engine execution | `agent-execution-engine` plus `agent-runtime-core` is a reasonable split for breaking the back-dependency. No new engine-layer overdesign was found. |
| Microservice boundary | The six team-facing modules plus shared kernel, BoM, and graphmemory starter remain acceptable for L0. The main risk is now governance drift across duplicated topology prose. |

## Proposed rc8 acceptance criteria

1. Serial and parallel gates execute the same rule slug set, including Rule 86
   and Rule 87.
2. `bash gate/test_architecture_sync_gate.sh` fails closed when its pass count
   does not equal its expected total, and its final summary is internally
   consistent.
3. Root `ARCHITECTURE.md`, `docs/contracts/contract-catalog.md`,
   `architecture-status.yaml`, module metadata, DFX files, and the actual Java
   source agree on the owner of `GraphMemoryRepository`.
4. `agent-service/module-metadata.yaml` and `agent-runtime-core/ARCHITECTURE.md`
   reflect the post-ADR-0079 ownership split.
5. `docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml`
   contains exact, non-corrupt verification evidence.
6. `python gate/build_architecture_graph.py`, `bash gate/check_architecture_sync.sh`,
   `bash gate/check_parallel.sh`, `bash gate/test_architecture_sync_gate.sh`, and
   `.\mvnw.cmd clean verify` all pass with mutually consistent reported counts.

## Release-note recommendation

The next note should be an rc8 corrective note, not a completion note. Suggested
one-liner:

> v2.0.0-rc8 closes the rc7 post-corrective review gaps by aligning the
> GraphMemoryRepository ownership corpus, restoring serial/parallel gate parity,
> and making the gate self-test harness fail closed on summary-count drift.

