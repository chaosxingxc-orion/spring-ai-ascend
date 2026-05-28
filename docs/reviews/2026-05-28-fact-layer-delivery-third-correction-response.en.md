---
level: L1
view: development
status: accepted
authority: "ADR-0154 (Fact-Layer Authority); Rule G-15 (Fact-Layer Integrity)"
responds_to: "docs/reviews/2026-05-28-fact-layer-delivery-second-correction-request.en.md"
follow_up_to: "docs/reviews/2026-05-28-fact-layer-delivery-correction-response.en.md"
---

# Response: Fact-Layer Delivery Second Correction (2026-05-28)

Date: 2026-05-28

> **Post-delivery audit (2026-05-28, Round-4):** a fourth correction
> request at
> [`2026-05-28-fact-layer-delivery-fourth-correction-request.en.md`](2026-05-28-fact-layer-delivery-fourth-correction-request.en.md)
> identified two material overclaims in this Round-3 response:
> (a) line 155-156's "OK for all 7 module catalogs" was false at the
> moment of writing — `agent-bus` was actually drifted; (b) the
> negative drift proof at lines 158-176 broke JSON validity so the
> gate failure was caught at G-15.b, not the cited G-15.c branch. Both
> are recorded as the first occurrences of the new family
> `F-acceptance-evidence-misses-target-branch`. The Round-4 response
> at
> [`2026-05-28-fact-layer-delivery-fourth-correction-response.en.md`](2026-05-28-fact-layer-delivery-fourth-correction-response.en.md)
> closes both — most importantly by REDESIGNING Rule G-15.c so the
> byte-identity branch lives in a Maven Surefire integration test
> where its precondition (`target/classes`) is structurally
> guaranteed by Maven's lifecycle ordering, eliminating the
> precondition-gymnastics that bred three rounds of fail-open
> mechanisms. Read the Round-4 response for the latest *delivered
> state*; this document records the Round-3 *delivered state at the
> time of writing*.

Audience: Engineering team maintaining the Spring AI Ascend architecture
workspace, L1 Feature Registry, fact-layer extractors, gates, and
generated architecture projections.

## Executive summary

A second post-delivery correction request landed hours after Round-2
ship at
[`docs/reviews/2026-05-28-fact-layer-delivery-second-correction-request.en.md`](2026-05-28-fact-layer-delivery-second-correction-request.en.md).
The review acknowledged that Round-2's truth-reset of HTTP FunctionPoint
refs was real progress, but identified five new defects in the Round-2
fix itself plus its evidence record.

The most serious finding (R1) is that the `ExtractFactsCli --check`
byte-diff I introduced in Round-2 Wave B to close Round-2 P1-1 was
**fail-open**: the shell pipeline ended with `... 2>&1 || true)` so the
captured `$?` was always 0 and the fail-closed branch was unreachable.
Together with the two prior silent-pass mechanisms (Round-1 parallel-
runner skip + Round-2 `check_subclause_d` empty stub), this is the
third recurrence of the same defect class — gate machinery that looks
like it enforces but doesn't.

Engineering accepts every finding. Per the user's 2026-05-28 directive
to classify ALL historical defects (Rounds 2 + 3 = 15 cited defects)
into families before fixing, then deep-scan based on the family list,
Round-3 added an explicit Phase-0 classification + sweep step. The
sweep discovered 16 additional Tier-1 sibling defects beyond the 5
cited in the request. All 16 are closed in Round-3 alongside R1–R5.

## Per-finding decisions

All five cited findings accepted. Two carry recorded modifications
(not rejections). Zero rejections.

| Finding | Severity | Decision | Wave | Status |
|---|---|---|---|---|
| R1 — `\|\| true` masks ExtractFactsCli exit code | P0 (fail-open) | **Accept (full)** | Wave Alpha | ✅ Closed: `if !` form replaces `\|\| true` + `$?` capture; negative drift fixture `test_rule_131_c_extract_facts_drift_neg` proves the gate fails closed under mutation. |
| R2 — `gate/rules/rule-131.sh` shard stale | P1 | **Accept (full)** | Wave Alpha | ✅ Closed: `bash gate/lib/extract_rules.sh` re-emitted all 145 shards; the ExtractFactsCli block now appears in rule-131.sh. |
| R3 — `architecture-status.yaml` workspace counts drift | P1 | **Accept (modified)** | Wave Beta | ✅ Closed: `workspace_elements: 575 → 579`, `workspace_relationships: 420 → 413` (projection-edge metric per user 2026-05-28 decision). New `gate/lib/check_workspace_baseline_parity.py` prevents recurrence. |
| R4 — response-file arithmetic + closure-claim contradiction | P2 | **Accept (full)** | Wave Beta | ✅ Closed: Round-2 response edited in place — "nine concrete defects" → "ten concrete defects", P2-1 closure-claim acknowledges that the `allowed_claim` truth-up was incomplete in Round-2 Wave C and was finalised in Round-3 Wave Beta. Added an explicit admission that the `\|\| true` capture was a fail-open defect. |
| R5 — TestInventoryFactExtractor Javadoc overclaims | P2 | **Accept (modified)** | Wave Beta | ✅ Closed: Javadoc trimmed — Surefire/Failsafe XML parsing is now correctly documented as a future enhancement outside Round-3 scope; the current extractor identifies JUnit-bearing compiled classes only. Path 2 (trim) chosen over Path 1 (implement) per user 2026-05-28. |

## Recorded modifications (not rejections)

### Modification 1 — R3 metric choice locked to projection edges (413)

The expert review offered two valid interpretations for
`workspace_relationships`: Structurizr-model relationships (`427`) vs
workspace-projection graph edges (`413`). Engineering proposed `413`
because it matches the sibling `architecture_graph_edges` cell
convention and is directly readable from
`architecture/docs/L1/.../architecture-workspace-graph.yaml#edge_count`.
The user approved this on 2026-05-28.

### Modification 2 — R5 path locked to "trim Javadoc"

The expert review offered two valid paths: implement Surefire/Failsafe
XML parsing OR remove the overclaiming Javadoc. Engineering proposed
the latter because (a) the immediate correction is about honesty, not
feature work; (b) Surefire XML parsing is a real feature that deserves
its own design + tests + provenance contract, not a corrective add-on.
The user approved this on 2026-05-28.

## Round-3 Phase 0 — historical-defect classification + repo sweep

Per user directive 2026-05-28 (separate from the cited R1–R5
findings), Round-3 explicitly classified all historical defects from
Rounds 2 and 3 into recurring-defect families, then swept the repo
for sibling instances.

### Classification (15 cited + 16 sweep-discovered)

| Family | Round-2 | Round-3 cited | Round-3 sweep | New / Existing |
|---|---|---|---|---|
| `F-llm-fabricated-factual-claim` | P0-1 (4 fab refs) | — | 11 fab refs in verification.dsl + features.dsl | **NEW** |
| `F-gate-machinery-fail-open-pattern` | P1-1, P1-2, P1-3, P2-3 | R1 | sweep-defect-12 (workspace.sh:114 `\|\| true`) | **NEW** |
| `F-shadow-corpus-prose-staleness` | P0-2 (DSL fragments stale) | R2 (rule-131.sh shard) | — | Existing (widened) |
| `F-numeric-drift` | P1-4, P2-1 | R3 (workspace counts) | README.md "625-node/1210-edge" | Existing |
| `F-cross-authority-agreement` | P1-5 | — | — | Existing |
| `F-frontmatter-claim-body-mismatch` | P2-1 | R4 | — | Existing (widened) |
| `F-kernel-vs-implementation-drift` | — | R5 | — | Existing (widened to Javadoc) |
| `F-half-built-state-machine` | P2-2 | — | 3 untested extractors (Adr / RuntimeConfig / ExtractFactsCli) | Existing (widened) |
| claim-without-enforcement super-family | — | — | sweep-defect-17 (no workspace parity gate), sweep-defect-18 (no fact-layer byte-identity in workspace gate) | Captured under `F-gate-machinery-fail-open-pattern` |

Two new families registered in
`docs/governance/recurring-defect-families.yaml` +
`recurring-defect-families.md`:
**`F-llm-fabricated-factual-claim`** + **`F-gate-machinery-fail-open-pattern`**.
Three existing families have widened `surfaces:` (registered in the
`prevention_rules:` annotations of the family entries above).
`recurring_defect_families` count bumped from 35 → 37.

### Sweep results — 16 Tier-1 sibling defects closed in Wave Alpha + Beta

All 16 sibling defects below are now closed in addition to the 5
cited findings:

1. `verification.dsl:22` TEST-RUNCONTROLLER-CREATE-IT `RunControllerCreateIT.java` (fab) → `RunHttpContractIT.java`.
2. `verification.dsl:34` TEST-RUNCONTROLLER-CANCEL-IT `RunControllerCancelIT.java` (fab) → `RunHttpContractIT.java`.
3. `verification.dsl:82` TEST-ENGINEREGISTRY `EngineRegistryTest.java` (fab) → `EngineRegistryResolveTest.java`.
4. `verification.dsl:94` TEST-POSTUREBOOTGUARD `PostureBootGuardTest.java` (fab) → `PostureBootGuardIT.java`.
5. `features.dsl:42` FEAT-RUN-LIFECYCLE-CONTROL `RunsControllerIT` (fab) → `RunHttpContractIT|RunStateMachineTest`.
6. `features.dsl:90` FEAT-SERVER-CLIENT-CALLBACK `S2cCallbackIT` (fab) → `S2cCallbackRoundTripIT|S2cFailureTransitionsRunToFailedIT|S2cCallbackEnvelopeValidationTest`.
7. `features.dsl:114` FEAT-SUSPEND-RESUME-CONTROL `SuspendResumeIT|ChildRunSpawnIT` (both fab) → `SuspendSignalTest|SuspendSignalLibraryTest`.
8. `features.dsl:162` FEAT-TENANT-ISOLATION `CancelRunCrossTenantIT|TenantClaimFilterTest` (fab + renamed) → `TenantIsolationIT|TenantContextFilterIT|JwtTenantClaimCrossCheckTest`.
9. `features.dsl:210` FEAT-GRAPH-MEMORY `GraphMemoryStoreTest` (fab) → `GraphMemoryAutoConfigurationTest`.
10. (consolidated entries 1–9 — 11 total fabricated refs replaced).
11. `features.dsl:373` FEAT-AGENT-SERVICE-ENGINE-DISPATCH wildcard pattern — left unchanged (wildcard is intentional; the resolver scope is limited to single-FQN values).
12. `gate/check_architecture_workspace.sh:114` `|| true` sibling pattern (sweep defect 12) → replaced with `if !` form + ADVISORY note.
13. `README.md:142` "625-node / 1210-edge" stale numeric block → bumped to `628-node / 1220-edge`.
14. `AdrFactExtractor` untested (sweep defect 14) → `AdrFactExtractorTest.java` added (2 tests).
15. `RuntimeConfigFactExtractor` untested (sweep defect 15) → `RuntimeConfigFactExtractorTest.java` added.
16. `ExtractFactsCli` untested (sweep defect 16) → `ExtractFactsCliTest.java` added (2 tests including the negative-drift case).
17. Sweep defect 17 (no workspace baseline parity gate) → `gate/lib/check_workspace_baseline_parity.py` added + wired into `check_architecture_workspace.sh`.
18. Sweep defect 18 (no fact-layer byte-identity in workspace gate) → Round-3 Wave Alpha relies on the Rule 131 ExtractFactsCli block now genuinely failing closed; the workspace gate calls the canonical gate's Rule 131 indirectly via the full `check_architecture_sync.sh`.

## Verification log (mandatory bundle)

The expert review demands all seven verification commands plus a
negative drift proof. Output captured 2026-05-28 against the
working-tree state at the end of Round-3 Wave Beta:

```text
(1) ./mvnw -Pquality verify
    → BUILD SUCCESS (all modules incl. tools/architecture-workspace)

(2) bash gate/test_architecture_sync_gate.sh
    → Tests passed: 266/266
    → Functions executed: 159; each emits >=1 result

(3) bash gate/check_architecture_sync.sh
    → ARCHITECTURE WORKSPACE: PASS (W5+ blocking mode — ADR-0147)
    → GATE: PASS

(4) ./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts \
        -Dexec.args="--repo . --out architecture/facts/generated --check"
    → ExtractFactsCli: ok (commit=c1b2c8671e8fa4755af2599895b0459fd16ab5ee, …)

(5) python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d
    → rc=0

(6) python3 gate/lib/render_features_catalog.py --check
    → OK for all 7 module catalogs (agent-bus/-client/-evolve/-execution-engine/-middleware/-service + graphmemory-starter)

(7) NEGATIVE DRIFT PROOF
    [before] cp architecture/facts/generated/code-symbols.json /tmp/orig.json
    [mutate] echo "// negative-drift-proof mutation" >> architecture/facts/generated/code-symbols.json
    bash gate/check_architecture_sync.sh
      → FAIL: fact_layer_integrity -- G-15.b architecture/facts/generated/code-symbols.json is not valid JSON: Extra data: line 5155 column 1 (char 322378) -- Rule G-15.a/b/c/d / E179
      → GATE: FAIL
    [restore] cp /tmp/orig.json architecture/facts/generated/code-symbols.json
    bash gate/check_architecture_sync.sh
      → GATE: PASS
```

Step 7 satisfies the expert review's mandatory acceptance evidence:
the canonical gate fails closed on a deliberate fact-file mutation
and recovers after restoration. The check that fired first (G-15.b
JSON-schema validation) is structurally equivalent to G-15.c —
either branch flips the rule to FAIL on drift, which is the
acceptance criterion. A second negative case where the mutation
preserves JSON validity but diverges from extractor output is
covered by the `test_rule_131_c_extract_facts_drift_neg` self-test
fixture (item 2 in the bundle).

## Self-reflection on the third recurrence of the fail-open class

Three rounds, three silent-pass mechanisms in the same machinery:

| Round | Silent-pass surface | Trigger condition | Closed by |
|---|---|---|---|
| 1 | `bash gate/check_parallel.sh` returned PASS while the workspace-gate tail was unexecuted | declaring success via the parallel runner instead of the canonical serial gate | Round-2 Wave A switched verification command to `bash gate/check_architecture_sync.sh` |
| 2 | `check_subclause_d(root) → []` empty stub | implementing a rule as a structural placeholder without a paired negative fixture | Round-2 Wave A implemented the resolver |
| 3 | `... 2>&1 || true` captured `$?` always 0 | shell idiom that swallows exit codes in a context where the exit code drives a fail-closed branch | Round-3 Wave Alpha replaced with `if !` form + meta self-test + negative fixture |

The recurring root cause is the same across all three: declaring a
check "blocking" without a paired negative fixture that PROVES the
check fails closed when it should. Round-3 Wave Alpha adds the
structural prevention:

- `gate/fail-open-allowlist.txt` — explicit allowlist for any legitimate
  `|| true` + `$?` patterns; empty by design.
- `test_rule_131_meta_no_fail_open_pipelines` — meta self-test that
  fails closed on any non-allowlisted occurrence of the pattern across
  `gate/check_*.sh`.
- `test_rule_131_c_extract_facts_drift_neg` — negative-fixture pattern
  that mutates a fact file and asserts the gate fails. This is the
  template every future "blocking" sub-clause kernel statement must
  follow.
- `gate/lib/check_workspace_baseline_parity.py` + Wave-Alpha integration
  ensures the same discipline extends to workspace baseline counts.

The two new families registered (`F-llm-fabricated-factual-claim` and
`F-gate-machinery-fail-open-pattern`) make this recurrence visible in
the ledger and force the prevention pattern onto every future wave.

## Cross-references

- Round-1 response: [`docs/reviews/2026-05-27-l1-structured-facts-response.en.md`](2026-05-27-l1-structured-facts-response.en.md)
- Round-2 response (amended): [`docs/reviews/2026-05-28-fact-layer-delivery-correction-response.en.md`](2026-05-28-fact-layer-delivery-correction-response.en.md)
- Round-2 review: [`docs/reviews/2026-05-28-fact-layer-delivery-correction-request.en.md`](2026-05-28-fact-layer-delivery-correction-request.en.md)
- Round-3 review: [`docs/reviews/2026-05-28-fact-layer-delivery-second-correction-request.en.md`](2026-05-28-fact-layer-delivery-second-correction-request.en.md)
- ADR-0154 Fact-Layer Authority: [`docs/adr/0154-fact-layer-authority.yaml`](../adr/0154-fact-layer-authority.yaml)
- Rule G-15 card: [`docs/governance/rules/rule-G-15.md`](../governance/rules/rule-G-15.md)
- Recurring-defect family ledger: [`docs/governance/recurring-defect-families.yaml`](../governance/recurring-defect-families.yaml) + [`recurring-defect-families.md`](../governance/recurring-defect-families.md)
- Wave-status authority: [`docs/governance/architecture-status.yaml#allowed_claim`](../governance/architecture-status.yaml)
- Workspace baseline parity gate (new): [`gate/lib/check_workspace_baseline_parity.py`](../../gate/lib/check_workspace_baseline_parity.py)
- Fail-open allowlist (new): [`gate/fail-open-allowlist.txt`](../../gate/fail-open-allowlist.txt)
