---
affects_level: L0
affects_view: process
proposal_status: review
authors: ["Codex architecture review"]
responds_to:
  - docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md
  - docs/releases/2026-05-18-l0-rc8-corrective.en.md
related_adrs:
  - ADR-0069
  - ADR-0074
  - ADR-0078
  - ADR-0079
  - ADR-0080
  - ADR-0081
  - ADR-0082
related_rules:
  - Rule 25
  - Rule 37
  - Rule 42
  - Rule 46
  - Rule 66
  - Rule 78
  - Rule 82
  - Rule 84
  - Rule 85
  - Rule 87
  - Rule 88
  - Rule 89
affects_artefact:
  - AGENTS.md
  - ARCHITECTURE.md
  - README.md
  - CLAUDE.md
  - docs/CLAUDE-deferred.md
  - docs/STATE.md
  - docs/contracts/contract-catalog.md
  - docs/dfx/agent-platform.yaml
  - docs/adr/0080-resilience-contract-spi-package-alignment.yaml
  - docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml
  - docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml
  - docs/governance/architecture-status.yaml
  - docs/governance/rules/rule-37.md
  - docs/governance/rules/rule-42.md
  - docs/governance/rules/rule-46.md
  - docs/governance/rules/rule-85.md
  - docs/governance/rules/rule-82.md
  - docs/governance/rules/rule-88.md
  - docs/governance/rules/rule-89.md
  - docs/governance/enforcers.yaml
  - docs/governance/skill-capacity.yaml
  - docs/governance/sandbox-policies.yaml
  - docs/releases/2026-05-18-l0-rc8-corrective.en.md
  - gate/README.md
  - gate/lib/orchestrator.sh
  - gate/lib/extract_rules.sh
  - gate/rules/
  - gate/check_architecture_sync.sh
  - gate/check_parallel.sh
  - gate/test_architecture_sync_gate.sh
  - agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/SkillCapacityRegistry.java
  - agent-service/src/main/java/ascend/springai/service/platform/resilience/ResilienceAutoConfiguration.java
  - agent-service/src/test/java/ascend/springai/service/platform/architecture/McpReplaySurfaceArchTest.java
---

# L0 rc8 Post-Corrective Architecture Review

## Executive verdict

Do **not** publish a no-findings L0 completion release note yet.

The core agent runtime architecture is now close to L0-complete and I do not
recommend rejecting the main component design. The separation among
`agent-runtime-core`, `agent-service`, `agent-execution-engine`, and
`agent-middleware` is coherent. Dynamic planning is appropriately staged as a
W2 scheduler-admission `PlanProjection` rather than a premature W4 planner.
The skill-capacity / `ResilienceContract` split, memory and knowledge ownership
boundary, engine envelope, S2C checked suspension path, and GraphMemoryRepository
ownership decision are directionally sound for L0.

However, rc8 still has several L0-relevant corpus, contract, authority, and
constraint defects. They do not require a runtime architecture rewrite, but
they do block a clean "architecture complete" release note because they affect
the sources new architects and autonomous agents will use to decide what is
current, enforced, public, and deferred.

Root cause in one sentence: rc8 fixed the previous memory and gate defects
locally, but the prevention gates are mostly one-directional ("declared rows
must be valid") rather than exhaustive ("all public/current surfaces must be
declared"), leaving contract omissions, orphan authorities, and active/deferred
wording outside the newer truth checks.

## Verification performed

Scope note: this review intentionally ignores uncommitted local CI-fix worktree
changes that are not part of the rc8 GitHub-visible corpus.

| Command | Result |
|---|---|
| `python gate/build_architecture_graph.py` | PASS. Wrote 348 nodes / 486 edges; graph validation OK. |
| `bash gate/test_architecture_sync_gate.sh` | PASS. `Tests passed: 149/149`; `Functions executed: 47`. |
| `bash gate/check_architecture_sync.sh` | PASS. Serial canonical gate exits `GATE: PASS`. |
| `bash gate/check_parallel.sh` | PASS, but the actual trailer says `parallel_summary: executed 102 rules; serial source defined 102 rules`, contradicting rc8 baseline prose that says 74. |
| `.\mvnw.cmd clean verify` | PASS. Full 10-project Maven reactor succeeds. Parsed reports: 371 tests, 0 failures, 0 errors, 34 skipped. |

## Agent Architecture And Overdesign Assessment

No material overdesign was found in the agent-facing component split itself:

| Area | Assessment |
|---|---|
| Dynamic planning | `docs/contracts/plan-projection.v1.yaml` is correctly `design_only`; it bridges W2 scheduler admission to W4 planner DAG without shipping a half-planner. |
| Skills and capacity | The two-axis capacity model is useful and not excessive for L0, but the active/deferred language around S2C capacity must be corrected. |
| Memory and knowledge | ADR-0082's decision to keep `GraphMemoryRepository` in `agent-service` is a reasonable low-risk correction. The C-side/S-side ownership boundary remains appropriate. |
| Engine contracts | `EngineEnvelope` plus strict `EngineRegistry` matching is a good L0 boundary; it avoids a universal semantic DSL. |
| S2C callbacks | Checked suspension through `SuspendSignal.forClientCallback(...)` is the right architectural shape. The known synchronous bridge and S2C capacity wiring must stay explicitly deferred. |
| Governance layer | This is where over-complexity risk remains: the gate now has multiple count vocabularies, but the baseline ledger is not derived from the actual executable manifest. |

## Findings

### P0-1 - Gate baseline claims still say 74, but both serial and parallel gates execute 102 sections

The rc8 wave claims that Rule 88 restored serial/parallel parity and that both
gates execute 74 active gate rules. The real parallel gate output from this
review says otherwise:

```text
parallel_summary: executed 102 rules; serial source defined 102 rules
GATE: PASS
```

This means rc8 did fix parity, but the baseline metric and release evidence are
still not tied to the executable manifest.

Evidence:

| Artifact | Evidence |
|---|---|
| `docs/governance/architecture-status.yaml:99` | `active_gate_checks: 74`, described as "rules in gate/check_architecture_sync.sh". |
| `docs/governance/architecture-status.yaml:107` | `allowed_claim` repeats "74 active gate rules". |
| `README.md:15` | Public baseline repeats "74 active gate rules". |
| `gate/README.md:3`, `:18`, `:19` | Gate entrypoint says "74 active gate rules" for both parallel and serial commands. |
| `docs/releases/2026-05-18-l0-rc8-corrective.en.md:25`, `:97`, `:118`, `:119` | Release note repeats 74 and even states the expected `parallel_summary` should be `executed 74 rules; serial source defined 74 rules`. |
| Actual command output | `bash gate/check_parallel.sh` prints `executed 102 rules; serial source defined 102 rules`. |
| `gate/check_architecture_sync.sh` | The rule-header manifest contains 102 extractable gate sections under the Rule 88 header convention. Numeric top-level rule ids now run through Rule 89. |

Impact:

This is a release-evidence defect, not a Java runtime defect. It undermines the
rc8 claim that baseline metrics are single-source and that Rule 88 "enforces
the truth". Rule 88 currently enforces serial-vs-parallel equality, not equality
between `architecture-status.yaml#baseline_metrics.active_gate_checks` and the
actual canonical manifest.

Recommendation:

1. Decide the vocabulary explicitly:
   - If `active_gate_checks` means executable sections, update it to 102 and
     update README, gate README, architecture-status allowed claim, and rc8
     release note.
   - If the team wants to preserve 74 as a historical family count, rename it
     to a separate field such as `active_gate_rule_families`, and add a new
     `executable_gate_sections: 102` field for the manifest count.
2. Extend Rule 82 or Rule 88 so `baseline_metrics.active_gate_checks` is
   computed from the same manifest extraction used by `gate/check_parallel.sh`.
3. Add a negative self-test where YAML says 74 while the mock canonical
   manifest contains 102 headers; the gate must fail.
4. Update `gate/check_architecture_sync.sh` top comments as well. Its opening
   summary still describes an older "63 top-level active rules" era.

### P0-2 - `docs/STATE.md` is still an active, stale current-state source

AGENTS.md and README correctly state that the per-capability shipped/deferred
ledger is `docs/governance/architecture-status.yaml`. However, root
`ARCHITECTURE.md` still points readers to `docs/STATE.md` as "Current
per-capability state and maturity levels", and `docs/STATE.md` still exists
with pre-ADR-0078 / pre-ADR-0079 paths.

Evidence:

| Artifact | Evidence |
|---|---|
| `AGENTS.md:13-16` | Declares `docs/governance/architecture-status.yaml` as the authoritative shipped/deferred ledger and baseline source. |
| `README.md:105` | Says the canonical machine-readable index is `architecture-status.yaml`, and that an earlier README incorrectly linked to a non-existent `docs/STATE.md`. |
| `ARCHITECTURE.md:861` | Still says "Current per-capability state and maturity levels: `docs/STATE.md`". |
| `docs/STATE.md:9-18` | Current-state table points to deleted `agent-platform/...` and `agent-runtime/...` paths. |
| `docs/STATE.md:49-57` | Future capability rows still use deleted `agent-runtime/...` paths. |
| `docs/STATE.md:160` | Its own reading order still tells new team members to read `docs/STATE.md` as the per-capability shipped/deferred table. |

Impact:

This is a source-of-truth conflict in the exact area the rc waves have been
trying to stabilize. A new architect can follow the root L0 document and land
on a stale status ledger that contradicts the current 9-module topology,
GraphMemoryRepository ownership, and `agent-runtime-core` extraction.

Recommendation:

1. Either delete/archive `docs/STATE.md` and update `ARCHITECTURE.md:861` to
   point only to `docs/governance/architecture-status.yaml`, or rewrite
   `docs/STATE.md` as a thin archived pointer with clear non-authoritative
   front matter.
2. Add a gate rule that active, non-archived docs cannot cite `docs/STATE.md`
   as a current authority.
3. Extend current path-truth coverage beyond `agent-*/ARCHITECTURE.md` so stale
   `agent-platform/...` and `agent-runtime/...` path claims in active docs are
   rejected unless they carry an explicit historical marker.

### P0-3 - A deleted module still has an active DFX declaration on disk

ADR-0082 explicitly closed a deleted-module DFX family defect by requiring that
pre-Phase-C module DFX files not remain on disk. rc8 deleted
`docs/dfx/agent-runtime.yaml`, but `docs/dfx/agent-platform.yaml` still exists
as an active-looking current DFX declaration.

Evidence:

| Artifact | Evidence |
|---|---|
| `docs/dfx/agent-platform.yaml:1`, `:8` | The file says "DFX declaration for the agent-platform module" and declares `module: agent-platform`. |
| `docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml:119-120` | States that DFX files for deleted modules, including `agent-platform.yaml`, "MUST NOT remain on disk". |
| `docs/plans/phase-c-merge.md:46` | The Phase-C merge plan says `agent-platform.yaml` and `agent-runtime.yaml` should be folded into `agent-service.yaml`. |
| Local inventory | Current modules are `agent-bus`, `agent-client`, `agent-evolve`, `agent-execution-engine`, `agent-middleware`, `agent-runtime-core`, `agent-service`, `spring-ai-ascend-dependencies`, and `spring-ai-ascend-graphmemory-starter`; current DFX stems still include orphan `agent-platform`. |

Impact:

This is an authority conflict. A DFX file is not casual prose: Rule 78 uses DFX
as part of SPI/package truth, and the file carries platform, security,
observability, and artifact-coordinate claims for a module that no longer
exists. It also demonstrates that the gate checks required DFX files, but does
not reject orphan DFX files for deleted modules.

Recommendation:

1. Delete `docs/dfx/agent-platform.yaml`, or move it under an archive path with
   explicit historical front matter and remove it from active DFX discovery.
2. Add a gate rule that every `docs/dfx/<module>.yaml` stem must match a
   current module metadata row unless it lives under an archive directory.
3. Add an ADR-0082 regression fixture that leaves `docs/dfx/agent-platform.yaml`
   on disk and expects the gate to fail.

### P1-1 - Active rule kernels overclaim runtime enforcement that deferred docs correctly postpone

The active kernels in CLAUDE.md are more forceful than the current architecture
status, rule-card cross references, and deferred clauses. This creates a
logical conflict for implementers: one authoritative source says the behavior
is a current MUST, while another authoritative source says it is deferred.

Evidence:

| Rule | Active kernel claim | Deferred or status claim |
|---|---|---|
| Rule 42 | `CLAUDE.md:246` says the runtime `SandboxExecutor` MUST refuse logical grants wider than physical limits. | `docs/CLAUDE-deferred.md:290-296` says this is Rule 42.b deferred to W2; `docs/governance/sandbox-policies.yaml:20-23` says runtime enforcement is deferred; `docs/governance/rules/rule-42.md:23` also says runtime enforcement is deferred. |
| Rule 46 | `CLAUDE.md:276` and `docs/governance/rules/rule-46.md:12` say callbacks consume `s2c.client.callback` skill capacity. | `docs/governance/skill-capacity.yaml:66-73`, `docs/CLAUDE-deferred.md:332-340`, and `docs/adr/0074-s2c-capability-callback.yaml:145-149` say runtime S2C capacity admission is deferred to W2. |
| Rule 46 card | `docs/governance/rules/rule-46.md:30` says "46.b" is invalid-response lifecycle. | `docs/CLAUDE-deferred.md:332` defines 46.b as `ResilienceContract s2c.client.callback Wiring`; invalid response handling is already covered by current S2C tests and rule text. |

Impact:

This is not overdesign in the agent architecture. It is an active/deferred
boundary error. The design can legitimately defer sandbox enforcement and S2C
capacity wiring, but the CLAUDE kernel must say exactly that. Otherwise Rule 9
and Rule 28 appear violated because active MUST language has no runtime
enforcer.

Recommendation:

1. Narrow Rule 42's active kernel to the shipped obligation: schema and policy
   declaration plus the "no impossible policy widening" invariant. Move the
   runtime `SandboxExecutor.execute(...)` refusal sentence exclusively to
   Rule 42.b in `docs/CLAUDE-deferred.md`.
2. Narrow Rule 46's active kernel to: S2C envelope, checked suspension,
   response validation, failure transition, and declaration of the
   `s2c.client.callback` capacity row. State that runtime admission against
   that row is deferred to Rule 46.b.
3. Correct `docs/governance/rules/rule-46.md` so its deferred sub-clause list
   names 46.b as S2C capacity wiring and 46.c as non-blocking lifecycle.
4. Add a gate check for active rule kernels containing phrases like "runtime
   MUST" when the same rule has a matching deferred sub-clause for that exact
   runtime obligation.

### P1-2 - `SkillCapacityRegistry` is public SPI code but missing from the active SPI contract catalog

The contract catalog says there are 11 active SPI interfaces. That table is no
longer exhaustive for public interfaces under declared `.spi` packages. The
most important missing surface is `SkillCapacityRegistry`: ADR-0080 calls it a
registry SPI, ADR-0081 makes it part of runtime capacity admission, and Spring
autoconfiguration exposes it as an overrideable bean.

Evidence:

| Artifact | Evidence |
|---|---|
| `docs/contracts/contract-catalog.md:22` | Declares `Active SPI interfaces (11 total)`. |
| `docs/contracts/contract-catalog.md:63-71` | Separately lists `RunContext` and `TraceContext` as structural carriers, explaining why those two public `.spi` interfaces are excluded from the active SPI count. |
| `agent-service/src/main/java/ascend/springai/service/runtime/resilience/spi/SkillCapacityRegistry.java:14` | Declares `public interface SkillCapacityRegistry` under a `.spi` package. |
| `agent-service/src/main/java/ascend/springai/service/platform/resilience/ResilienceAutoConfiguration.java:26-30` | Registers a default `SkillCapacityRegistry` only when no user bean exists, making it a real Spring extension surface. |
| `docs/adr/0080-resilience-contract-spi-package-alignment.yaml:66` | Names `SkillCapacityRegistry.java` as "registry SPI". |
| `docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml:46-53` | Says `ResilienceContract.resolve(tenant, skill)` consults `SkillCapacityRegistry.tryAcquire(tenant, skill)`. |
| `docs/governance/rules/rule-85.md:12`, `:38`, `:53` | Validates catalog rows against metadata, but does not require every public `.spi` interface to appear in the catalog or be explicitly marked internal. |

Impact:

This is a contract completeness gap. The current gate proves that cataloged SPI
rows are backed by module metadata and DFX declarations; it does not prove that
all public SPI interfaces have been cataloged. That leaves a public
agent-capacity extension point outside the L0 contract ledger and outside the
binary-compatibility expectations that the catalog describes.

Recommendation:

1. Add `SkillCapacityRegistry` to the active SPI table, likely under
   `agent-service`, package `ascend.springai.service.runtime.resilience.spi`,
   with authority ADR-0070 / ADR-0080 / ADR-0081. If the architecture team
   deliberately does not want it to be a supported external SPI, mark it
   explicitly as `(internal)` in the catalog and explain the bean-override
   policy.
2. Update the `Active SPI interfaces (N total)` count and any per-module SPI
   count derived from it.
3. Strengthen Rule 85 or Rule 66 so every public `interface` under a declared
   `.spi` package must appear in `contract-catalog.md` either as active SPI or
   as an explicitly internal structural/helper surface.

### P1-3 - Deleted module names still appear in current enforcement prose outside the status-yaml guard

Rule 87 prevents stale `agent-platform` / `agent-runtime` claims in
`architecture-status.yaml#allowed_claim`, but equivalent current-tense claims
remain in root architecture constraints, rule cards, and test Javadocs.

Evidence:

| Artifact | Evidence |
|---|---|
| `ARCHITECTURE.md:811` | Constraint #59 says `McpReplaySurfaceArchTest` prevents `@RestController` under `agent-platform/web/replay`, `agent-platform/web/trace`, and `agent-platform/web/session`. The actual code is under `ascend.springai.service.platform.web...` after ADR-0078. |
| `agent-service/src/test/java/ascend/springai/service/platform/architecture/McpReplaySurfaceArchTest.java:17-20` | Javadoc says "The rule lives in agent-platform" and "agent-runtime hosts no HTTP endpoints", even though those modules were consolidated into `agent-service` and `agent-runtime-core`. |
| `docs/governance/rules/rule-37.md:22` | Active rule card still says the scope is intentionally narrow to `agent-runtime` and existing `agent-platform` JdbcTemplate uses are out of scope. |
| `CLAUDE.md:444` | Rule 87 only guards `architecture-status.yaml` allowed claims, so these active root/rule/test surfaces escape the current gate. |

Impact:

The actual tests still check the current package names, so this is not a
runtime failure. It is a contract-truth failure: an active L0 constraint teaches
the wrong module path and the gate does not cover that surface.

Recommendation:

1. Update `ARCHITECTURE.md:811` and the `McpReplaySurfaceArchTest` Javadoc to
   use current `agent-service.service.platform.web.*` language, with historical
   markers only where needed.
2. Update `docs/governance/rules/rule-37.md` to use `agent-service` package
   names and, if needed, point to the deferred R2DBC migration using current
   class names.
3. Generalize Rule 87 beyond `architecture-status.yaml` or add a sibling rule
   that scans active root architecture, rule cards, and active test Javadocs
   for current-tense deleted-module claims.

### P1-4 - Rule 89 has two incompatible self-test coverage definitions

The Rule 89 kernel in CLAUDE.md and the rule card define coverage for
prevention-wave rules only (`N >= 80`). Other active authority surfaces say
every rule in `check_architecture_sync.sh` must have a `test_rule_<N>_*`
function. The implementation follows the narrower prevention-wave definition.

Evidence:

| Artifact | Evidence |
|---|---|
| `CLAUDE.md:460` | Requires `test_rule_<N>_*` functions for every prevention-wave Rule (`N >= 80`), with Rules 1-79 grandfathered. |
| `docs/governance/rules/rule-89.md:12` | Repeats the same prevention-wave-only definition. |
| `docs/governance/enforcers.yaml:1101` | Says every `# Rule N -- slug` header must have a `test_rule_<N>_*` function, calling this "full coverage parity". |
| `gate/README.md:68` | Says every Rule defined in `check_architecture_sync.sh` has at least one `test_rule_<N>_*` function. |
| `gate/check_architecture_sync.sh:4319-4332` | The actual Rule 89 implementation only checks Rules `>=80`. |
| `bash gate/test_architecture_sync_gate.sh` output | Reports `Functions executed: 47`, not one function per 102 executable gate sections. |

Impact:

Either interpretation can be valid, but both cannot be authoritative at the
same time. If full coverage parity is required, the current harness is
under-tested. If prevention-wave coverage is the intended L0 scope, the
enforcer row, gate README, and rc8 release evidence overclaim the guarantee.

Recommendation:

1. Choose one coverage contract. For an L0 stabilization release, the lower
   risk path is to keep the prevention-wave scope but narrow `enforcers.yaml`,
   `gate/README.md`, and the rc8 release note to match CLAUDE.md and the
   implementation.
2. If the team wants full per-rule self-test coverage, make that a W2.x hardening
   item and expand the harness deliberately rather than implying the work is
   already complete.
3. Add a Rule 89 self-test that distinguishes the two meanings: a pre-80 rule
   without a fixture should pass only under the prevention-wave contract, while
   a post-80 rule without a fixture must fail.

### P2-1 - `gate/rules/` is an incomplete shadow rule corpus

The production parallel gate no longer depends on the checked-in per-rule
files, but `gate/lib/orchestrator.sh` still describes `gate/rules/` as durable
artifacts for IDE inspection, code review, and future unit testing. That
directory is incomplete and stale relative to the canonical monolith.

Evidence:

| Artifact | Evidence |
|---|---|
| `gate/lib/orchestrator.sh:2-9` | Describes a per-rule orchestrator over files produced from the canonical monolith. |
| `gate/lib/orchestrator.sh:27-30` | Says `gate/rules/` files exist as durable artifacts for inspection, review, and future testing. |
| `gate/lib/extract_rules.sh:17`, `:53-54` | Defines generated files under `gate/rules/` and marks them "DO NOT HAND-EDIT". |
| Local inventory | `gate/rules/` has 83 files and lacks numeric Rule files `067-071`, `073-074`, and `080-089`, while the canonical gate executes 102 sections. |

Impact:

This is lower severity because `gate/check_parallel.sh` delegates to the
canonical monolith. The risk is reviewer and agent confusion: the repository
contains a second rule corpus that looks generated and durable, but is not
fresh enough to be used as a truth source.

Recommendation:

1. Either remove `gate/rules/` from active source control and document it as a
   local generated artifact, or regenerate it and add a freshness check.
2. Update `gate/lib/orchestrator.sh` comments to state clearly that the
   production parallel gate ignores `gate/rules/`.
3. If the files stay tracked, add a gate check that `gate/lib/extract_rules.sh`
   produces a clean tree and includes every extractable canonical section.

## Suggested corrective wave

Recommended scope for the next architecture-team response:

1. **Baseline manifest truth** - fix the 74 vs 102 count taxonomy and make the
   baseline derive from the executable gate manifest.
2. **Orphan authority retirement** - remove/archive `docs/STATE.md` and
   `docs/dfx/agent-platform.yaml`; update the
   root architecture current-state pointer.
3. **SPI contract exhaustiveness** - add `SkillCapacityRegistry` to the SPI
   contract catalog, or explicitly classify it internal, and extend the gate so
   public `.spi` interfaces cannot be missed.
4. **Active/deferred kernel cleanup** - narrow Rule 42 and Rule 46 active
   kernels so they align with their deferred runtime sub-clauses.
5. **Deleted-module path truth sweep** - update root constraints, rule cards,
   and Javadocs still using current-tense `agent-platform` / `agent-runtime`
   wording.
6. **Self-test and shadow-corpus cleanup** - reconcile Rule 89's scope across
   CLAUDE.md, enforcers, gate README, implementation, and release notes; remove
   or refresh the incomplete `gate/rules/` corpus.
7. **Prevention gates** - add at least four structural checks:
   - baseline metric equals actual executable gate manifest count;
   - active docs cannot cite retired state ledgers or deleted module paths as
     current authority;
   - DFX file stems must match current module metadata unless archived;
   - every public `.spi` interface must be cataloged or explicitly internal.

## Release-note guidance

After the corrective wave, a clean release note should be possible if:

1. `bash gate/check_parallel.sh` trailer count matches
   `architecture-status.yaml#architecture_sync_gate.baseline_metrics`.
2. `ARCHITECTURE.md` no longer points current readers to `docs/STATE.md`.
3. `docs/dfx/agent-platform.yaml` is deleted or archived outside active DFX
   discovery.
4. `SkillCapacityRegistry` is represented in the SPI contract catalog or
   explicitly classified as internal.
5. Rule 42 and Rule 46 kernels no longer overclaim runtime behavior that is
   explicitly deferred.
6. Active architecture/rule/test prose no longer uses deleted module names in
   current-tense enforcement claims.
7. Rule 89 self-test coverage scope is consistent across CLAUDE.md,
   enforcers.yaml, gate README, the implementation, and release notes.
8. `gate/rules/` is either removed from the active authority surface or proved
   fresh against the canonical monolith.
9. The standard verification set remains green:
   `python gate/build_architecture_graph.py`,
   `bash gate/test_architecture_sync_gate.sh`,
   `bash gate/check_architecture_sync.sh`,
   `bash gate/check_parallel.sh`,
   and `.\mvnw.cmd clean verify`.
