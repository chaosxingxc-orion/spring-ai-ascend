---
title: "Fact-Layer Delivery Fourth Correction Request"
date: 2026-05-28
status: requested
audience: engineering
scope: "Round-1 response audit trail, Round-3 delivered state, feature catalog render drift, gate coverage"
review_target: "docs/reviews/2026-05-27-l1-structured-facts-response.en.md"
latest_delivery_claim: "docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md"
---

# Fact-Layer Delivery Fourth Correction Request

Engineering team,

This review re-audits the Round-1 response file
`docs/reviews/2026-05-27-l1-structured-facts-response.en.md` in its current
state. That file now points reviewers to the Round-3 response as the latest
delivered state. The Round-3 implementation fixed several serious defects:
the previous `|| true` exit-code mask is gone from the Rule 131 extractor
block; `gate/rules/rule-131.sh` has been refreshed; baseline workspace counts
now match the current projection; `TestInventoryFactExtractor` no longer
claims shipped Surefire/Failsafe XML parsing; and the core verification bundle
mostly passes.

However, the delivery still cannot be accepted as complete. One rendered L1
feature catalog is stale, the canonical gate did not catch it, and the
Round-3 negative drift evidence still does not prove the exact fail-closed
path that Rule G-15.c requires.

## Verification Results From This Review

Commands run during this review:

| Command | Result | Notes |
|---|---:|---|
| `./mvnw -Pquality verify` | PASS | Full Maven quality build succeeds. |
| `bash gate/test_architecture_sync_gate.sh` | PASS | `266/266` self-tests pass. |
| `bash gate/check_architecture_sync.sh` | PASS | Canonical gate passes, but this is a false negative for the feature-catalog drift below. |
| `./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"` | PASS | Extracted facts match the current generated fact files. |
| `python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d` | PASS | Fact-layer integrity checker passes. |
| `python3 gate/lib/check_workspace_baseline_parity.py` | PASS | Workspace baseline parity passes. |
| `python3 gate/lib/render_features_catalog.py --check` | FAIL | `DRIFT: architecture/docs/L1/agent-bus/features/README.md`. |

The failed `render_features_catalog.py --check` result is enough to block
acceptance because the original response explicitly accepted drift gates for
rendered L1 feature catalogs.

## Requirement 1 - Re-render the stale `agent-bus` L1 feature catalog

### Problem

`architecture/features/features.dsl` is the structured authority for feature
metadata, but the rendered `agent-bus` feature catalog is stale:

- `architecture/features/features.dsl:90` now declares
  `FEAT-SERVER-CLIENT-CALLBACK` verification tests as:
  `S2cCallbackRoundTripIT`, `S2cFailureTransitionsRunToFailedIT`, and
  `S2cCallbackEnvelopeValidationTest`.
- `architecture/docs/L1/agent-bus/features/README.md:106` still renders the
  old fabricated / stale value `S2cCallbackIT`.
- `python3 gate/lib/render_features_catalog.py --check` reports:
  `DRIFT: architecture/docs/L1/agent-bus/features/README.md`.
- `docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md:155-156`
  claims all seven module catalogs are OK, which is false in the reviewed
  workspace.

### Sources supporting the requirement

- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md:116-120`
  explicitly accepts drift gates for rendered L1 feature catalogs.
- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md:124-129`
  says Rule G-15 should not duplicate Rule G-13; rendered-surface coherence
  should be handled by G-13-style idempotency.
- `architecture/docs/L1/agent-bus/features/README.md:8` carries a
  `DO NOT HAND-EDIT` banner and says it is rendered from
  `architecture/features/features.dsl`.
- `architecture/features/features.dsl:90` and
  `architecture/docs/L1/agent-bus/features/README.md:106` currently disagree.

### Required change

Re-render all L1 feature catalogs from `architecture/features/features.dsl`
and commit the resulting byte-identical outputs. At minimum, the
`FEAT-SERVER-CLIENT-CALLBACK` section in
`architecture/docs/L1/agent-bus/features/README.md` must list the same three
verification test FQNs as `architecture/features/features.dsl`.

### Acceptance evidence required

- `python3 gate/lib/render_features_catalog.py --check` must pass for all
  seven module catalogs.
- The updated diff must show `agent-bus/features/README.md` replacing
  `S2cCallbackIT` with the three real test FQNs from `features.dsl`.
- The Round-3 response or a superseding response must correct the previous
  claim that all seven catalogs were already OK.

## Requirement 2 - Make feature-catalog render drift fail the canonical gate

### Problem

The canonical gate passed even though the feature catalog renderer detected
drift:

- `bash gate/check_architecture_sync.sh` returned `GATE: PASS`.
- `python3 gate/lib/render_features_catalog.py --check` returned
  `DRIFT: architecture/docs/L1/agent-bus/features/README.md`.
- `gate/check_architecture_sync.sh:6940` only delegates Rule 126 /
  Rule G-13 to `gate/lib/check_template_render_idempotency.py`.
- The feature-catalog renderer is a separate script
  (`gate/lib/render_features_catalog.py`) and is not invoked by the canonical
  gate.
- The Rule 126 comment at `gate/check_architecture_sync.sh:6949-6952` still
  says the template list is empty / forward-looking, which is stale relative
  to the non-empty `surface-classification.yaml` and does not cover feature
  catalogs.

This is a gate-coverage defect: the repository has a tool that detects the
drift, but the canonical acceptance command does not fail on it.

### Sources supporting the requirement

- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md:116-120`
  includes "rendered L1 feature catalogs" in the accepted drift-gate scope.
- `docs/governance/rules/rule-G-13.md:75-82` states that every templated or
  hybrid rendered output must be byte-identical to its source render.
- `gate/check_architecture_sync.sh:6934-6954` shows the current Rule 126
  implementation path and omission.
- `gate/lib/render_features_catalog.py --check` is the existing detector for
  this exact rendered surface.

### Required change

Wire feature-catalog render idempotency into the canonical gate. Use one of
these acceptable approaches:

1. register each `architecture/docs/L1/<module>/features/README.md` output in
   `docs/governance/templates/surface-classification.yaml` with a context
   loader / renderer that delegates to `render_features_catalog.py`; or
2. add an explicit canonical gate sub-check that runs
   `python3 gate/lib/render_features_catalog.py --check` and fails closed on
   any drift.

The implementation must include a negative fixture that mutates one rendered
feature catalog while leaving `features.dsl` unchanged, then proves
`bash gate/check_architecture_sync.sh` fails.

### Acceptance evidence required

- `bash gate/check_architecture_sync.sh` must fail after a deliberate feature
  catalog mutation and pass after re-render.
- `bash gate/test_architecture_sync_gate.sh` must include a new negative test
  for feature-catalog render drift.
- `python3 gate/lib/render_features_catalog.py --check` must pass.

## Requirement 3 - Remove the G-15.c compiled-classes advisory skip from the default canonical path

### Problem

Round-3 fixed the `|| true` return-code mask, but preserved a second
fail-open path for Rule G-15.c:

- `gate/check_architecture_sync.sh:7093-7100` runs `ExtractFactsCli --check`
  only if `agent-service/target/classes` exists. Otherwise it emits an
  advisory line and still allows Rule 131 to pass.
- `gate/test_architecture_sync_gate.sh:7193-7195` skips the G-15.c negative
  fixture when `agent-service/target/classes` is absent.

Rule G-15.c is described as blocking, not advisory. A canonical gate run that
cannot evaluate the extractor byte-diff must not silently pass as if the check
was satisfied.

### Sources supporting the requirement

- `CLAUDE.md:409` states that generated facts under
  `architecture/facts/generated/` must be byte-identical to extractor
  re-emission at the same workspace HEAD.
- `architecture/facts/README.md:151` marks G-15.c
  (`ExtractFactsCli --check` byte-diff) as blocking.
- `gate/check_architecture_sync.sh:7093-7100` currently downgrades missing
  compiled classes to an advisory skip.
- `gate/test_architecture_sync_gate.sh:7193-7195` lets the negative fixture
  skip in the same condition.

### Required change

Choose a fail-closed default:

1. make the canonical gate build or refresh the required compiled classes
   before `ExtractFactsCli --check`; or
2. fail Rule 131.c with a clear remediation message when required compiled
   classes are absent; or
3. provide an explicit opt-in advisory flag for local exploratory runs, while
   the default `bash gate/check_architecture_sync.sh` path remains blocking.

Do not keep "missing compiled classes" as a default PASS path for a blocking
rule.

### Acceptance evidence required

- A self-test must cover the missing-classes path and prove it fails closed by
  default.
- The existing G-15.c drift negative fixture must not be silently skipped in
  the default verification path.
- The delivery response must state the build/precondition semantics honestly.

## Requirement 4 - Replace the G-15.c negative proof with a valid-JSON canonical-gate proof

### Problem

The Round-3 response claims the negative drift proof covers G-15.c, but the
recorded mutation is invalid JSON:

- `docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md:160`
  appends a comment line to `code-symbols.json`.
- `docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md:162`
  shows the canonical gate failing at G-15.b JSON validation, not at the
  `ExtractFactsCli --check` byte-diff branch.
- `docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md:174-176`
  claims a valid-JSON divergence is covered by
  `test_rule_131_c_extract_facts_drift_neg`.
- `gate/test_architecture_sync_gate.sh:7207` also appends a comment line,
  which is invalid JSON.
- `gate/test_architecture_sync_gate.sh:7209` invokes `ExtractFactsCli --check`
  directly, not the canonical `bash gate/check_architecture_sync.sh` path
  whose shell propagation was the actual Round-3 defect.

This means the response still lacks proof for the exact failure class: a
schema-valid generated fact file that differs from extractor re-emission must
make the canonical gate fail through G-15.c.

### Sources supporting the requirement

- `CLAUDE.md:409`: G-15.c is byte-identical extractor re-emission, not only
  JSON validity.
- `architecture/facts/README.md:151`: the byte-diff check is blocking.
- `docs/reviews/2026-05-28-fact-layer-delivery-third-correction-response.en.md:158-176`:
  current proof falls through G-15.b and overclaims valid-JSON coverage.
- `gate/test_architecture_sync_gate.sh:7207-7209`: current self-test uses an
  invalid JSON comment and bypasses the canonical gate shell block.

### Required change

Add a negative fixture that:

1. edits a generated fact file while keeping it valid JSON and schema-valid;
2. leaves the `DO NOT EDIT` banner intact;
3. runs `bash gate/check_architecture_sync.sh`, not only
   `ExtractFactsCli --check`; and
4. asserts the failure message reaches the Rule 131.c
   `ExtractFactsCli --check drift` branch.

The direct `ExtractFactsCli --check` unit test can remain, but it is not a
substitute for canonical-gate propagation evidence.

### Acceptance evidence required

- Show the new valid-JSON mutation fixture and its failure assertion.
- Show `bash gate/test_architecture_sync_gate.sh` passing with the new test.
- Show a manual negative proof:
  mutate a generated fact in a schema-valid way, run
  `bash gate/check_architecture_sync.sh`, observe a Rule 131.c
  `ExtractFactsCli --check drift` failure, restore/regenerate, then observe
  the gate passing.

## Required Resubmission Bundle

The next delivery response must include:

1. `./mvnw -Pquality verify`
2. `bash gate/test_architecture_sync_gate.sh`
3. `bash gate/check_architecture_sync.sh`
4. `python3 gate/lib/render_features_catalog.py --check`
5. `python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d`
6. `./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"`
7. Negative proof A: mutate a rendered feature catalog, show the canonical
   gate failing, re-render, show it passing.
8. Negative proof B: mutate a generated fact in a valid-JSON way, show the
   canonical gate failing through Rule 131.c, regenerate, show it passing.

Until these are satisfied, the fact-layer delivery remains directionally
correct but not fully accepted. The unresolved issue is the same pattern the
project is trying to eliminate: AI-facing factual surfaces can still drift
from their structured authority while the canonical gate reports success.

