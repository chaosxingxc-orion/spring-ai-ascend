---
level: L1
view: scenarios
affects_level: L0, L1
affects_view: [logical, process, development]
proposal_status: findings_log
date: 2026-05-23
authors: ["Claude Code (Opus 4.7) — adversarial-reviewer agent", "chao (curation)"]
responds_to:
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md
  - docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml
  - docs/adr/0109-s2c-and-ingress-server-identity-proof.yaml
  - docs/adr/0110-audit-tamper-evidence-and-hook-pii-failsafe.yaml
  - docs/adr/0112-engine-stateless-executor-value-based-yield.yaml
  - docs/adr/0114-implementation-feasibility-batched-closures.yaml
  - docs/adr/0115-agent-service-l1-expansion-acceptance.yaml
related_adrs:
  - ADR-0108
  - ADR-0109
  - ADR-0110
  - ADR-0112
  - ADR-0114
  - ADR-0115
---

# rc34 Adversarial-Review Follow-Up Findings

> Adversarial review of the rc34 branch (`rc34/deferred-finding-adr-closures`, HEAD `db78634`) surfaced 10 findings. Six were closed in-wave (path-truth prose, javadoc-vs-ADR coherence, graph-builder `supersedes_partial`, R-C.2.a scope widening, E105 enforcer path retarget, cosmetic family-summary staleness). Four require design decisions that should not be made unilaterally in a doc-fix wave; they are tracked here as named W2+ follow-ups so the "no bugs open" status of rc34 is honest about what's deferred and why.

## Findings closed in rc34 (no action required)

| ID | Title | Closure |
|---|---|---|
| ADV-01 | ADR-0112 vs `StatelessEngine.java` SPI contradiction | Both ADR-0112 Part A prose and the Java javadoc clarified: shipped SPI is synchronous `StateDelta execute(AgentInvokeRequest)`; Mono+InterruptSignal is the W0.5+ forward direction per Part C. No code change. |
| ADV-02 | ADR-0108 vs `RunController.java` 403/WARN gap | ADR-0108 decision section prefaced with "Current shipped state" callout marking 403+WARN as W1 widening direction, not W0 shipped behaviour. No code change. |
| ADV-04 | ADR-0114 / ADR-0115 / ADR-0111 / ADR-0109 phantom-file references | All `(W2 NEW)` / `(W0 NEW)` / "Ships with this ADR wave" prose revised to `(W2 follow-up — not yet authored)`. Five referenced docs (`virtual-thread-pinning-hazards.md`, `agent-runtime-core-dissolution-migration.md`, `agent-client-wire-contract-compatibility.md`, `otlp-tenant-routing.yaml`, `secret-lifecycle.yaml`, `key-distribution.yaml`, `dual-track-routing-policy.yaml`) now marked as follow-ups, not present-tense deliverables. |
| ADV-05 | `gate/build_architecture_graph.py` silently drops `supersedes_partial:` | Added branch at line 286 for `supersedes_partial` edge type. ADR-0112 → ADR-0019 edge will now produce on next graph rebuild. |
| PS-01 | `recurring-defect-families.md` `(12 families as of rc32)` heading staleness | Bumped to `(12 families as of rc34)`. Cosmetic. |
| PS-02 | Rule R-C.2.a scope gap + E105 stale `agent-runtime-core` path | Rule R-C.2.md `scope_surfaces` widened to include `service/task/**` + `service/session/**`. E105 `asserts` text retargeted to `agent-service/.../service/{runtime,task,session}/` (was dissolved `agent-runtime-core` per ADR-0088). |

## Findings deferred to W2+ (named follow-ups; design decisions required)

The following four findings name real defects that warrant resolution but exceed the scope of a doc-fix wave. Each is recorded here with a recommended remedy + named wave + owner cue.

### ADV-03 — A2A state vocabulary case mismatch (yaml lowercase vs Java uppercase)

- **Surface**: `docs/contracts/a2a-envelope.v1.yaml` lines 55–56 declare `task_states` enum as `[submitted, working, input_required, completed, failed]` (lowercase). `agent-service/src/main/java/com/huawei/ascend/service/task/Task.java` declares `A2aState` as `SUBMITTED, WORKING, INPUT_REQUIRED, COMPLETED, FAILED` (uppercase).
- **Risk**: When the contract is promoted from `design_only` to `runtime_enforced` (gated on first A2A interop test per ADR-0115 Part E), the yaml-schema validator will reject every `A2aState.name()` serialisation unless an explicit case-translation marshaller ships.
- **Decision required**: which case is canonical at the wire? Options:
  - (A) Java keeps uppercase; marshaller does `.toLowerCase()` on out and `.toUpperCase()` on in. Add Jackson `@JsonValue` + `@JsonCreator` on `A2aState`.
  - (B) Java migrates to lowercase enum constants (unconventional but eliminates the transform).
  - (C) Wire format swaps to `WIRE_FORMAT: SCREAMING_SNAKE_CASE` notation in `a2a-envelope.v1.yaml`; aligns with A2A spec's case convention if available.
- **Recommended**: Option (A) — Jackson-side translation. The yaml-schema marshaller owns the transform; Java keeps idiomatic SCREAMING_SNAKE_CASE.
- **Wave**: W2 (alongside the first A2A interop test).
- **Tracked-at**: ADR-0115 Part E + `architecture-status.yaml#a2a_protocol_boundary` row.

### ADV-06 — Task → Run 1:N relationship has no carrier field

- **Surface**: ADR-0115 Part B asserts "One Task maps 1:N to Runs" with diagram. `service/task/Task.java` record has no `runId` / `runIds` / `Set<UUID>` field. `service/runtime/runs/Run.java` has no `taskId` field. `TaskStateStore` SPI exposes `save(taskId, tenantId, state)` + `load(taskId, tenantId)` — neither direction is navigable.
- **Risk**: Any downstream consumer (`DualTrackRouter`, A2A peer querying task status, audit reconstruction) needs `runsByTask(taskId)` or `tasksByRun(runId)`. Adding the carrier later requires schema migration with no backfill path.
- **Decision required**: which direction holds the link?
  - (A) `Task.runIds: Set<UUID>` — Task carries the list of Runs it has spawned. Read path: `Task → Runs[]` is local.
  - (B) `Run.taskId: UUID` — each Run points up to its parent Task. Read path: `Run → Task` is direct; `Task → Runs[]` requires a query (`runRepo.findByTaskId(taskId)`).
  - (C) Separate `task_run_binding` join table for many-to-many flexibility.
- **Recommended**: Option (B) — `Run.taskId` reverse pointer. Matches the Run-as-persistence-spine stance (S3): Run owns its identity; Task is a coordination layer over Runs. ADR-0106 (Run.version migration) sets precedent for adding columns to Run with two-phase rollout.
- **Wave**: W2 — same wave as `task/repository/` JPA/R2DBC repository per ADR-0115 Part F tree.
- **Tracked-at**: `architecture-status.yaml#four_layer_state_model` row + new Flyway migration to be authored.

### ADV-09 — ADR-0110 hash-chain block-on-tamper can weaponise into tenant-level DOS

- **Surface**: ADR-0110 Part 1 lines 51–54 declares "chain mismatch raises `AuditChainBroken`; runtime emits CRITICAL audit + **blocks further writes for that tenant** until operator unblocks (forensic preservation)".
- **Risk**: a legitimate GDPR redaction (ADR-0114 δ.13 W3 erasure) breaks the chain; the verifier on the next emission blocks all subsequent audit writes for that tenant. Compounding with ADR-0108 + Rule R-J .b: cancel / get operations that depend on WARN+ audit emission also block. Compounding with ADR-0110 Part 2 `@SafetyCritical`: hook failures that transition Run to FAILED would cascade because their own audit emission would also block.
- **Decision required**: fail-degraded mode for chain breaks.
  - (A) On chain break: log CRITICAL, continue accepting writes with `chain_broken: true` marker on each new row, isolating the broken segment. Allows downstream operator to rebuild the chain on a clean segment without blocking the tenant.
  - (B) Posture-aware: at `posture=dev` log + continue; at `posture=research|prod` block (current ADR-0110 wording = prod-only behaviour for all postures).
  - (C) Per-tenant override flag: operators can pre-authorise legitimate redactions to skip block-on-tamper.
- **Recommended**: combine (A) + (B). Defaults vary by posture; `chain_broken: true` marker propagates regardless so a future W3 rebuilder has clean segment boundaries.
- **Wave**: ADR-0110 amendment (this wave is doc-only-no-amend; suggested rc35 follow-up ADR).
- **Tracked-at**: `architecture-status.yaml#audit_tamper_evidence` row (need to add) + recurring-defect-families candidate "F-availability-coupled-to-integrity-check" (not yet promoted; 1-instance observation).

### ADV-10 — ADR-0109 nonce-dedup + ADR-0114 7-day TTL = replay-window cliff

- **Surface**: ADR-0109 line 63–64 declares "Nonce dedup reuses IdempotencyStore mechanism (ADR-0027)"; replay window default 300s (line 49). ADR-0114 δ.13 lines 147–149 declares "rows older than `idempotency_ttl_days` (default 7) deleted by daily batch".
- **Risk**: attacker captures a signed envelope at T=0. Within 300s, nonce-dedup catches replay. After 7 days + 1 minute, the original idempotency-store row is GC'd. If the runtime's `iat within replay window` check is loose (only checks current clock against iat without a hard upper bound), the 7-day-stale envelope is admitted at T=7d+1m because nonce row is gone.
- **Additional gap**: ADR-0111 Part C `on_leak_procedure` for compromised JWT keys requires rotation. But envelopes signed with a compromised key BEFORE rotation remain replayable until day-7 nonce GC. Rotation does not accelerate nonce invalidation.
- **Decision required**: pick TWO independent bounds.
  - (A) Nonce-dedup TTL ≥ replay-window. ADR-0114 δ.13 default of 7d already satisfies this if replay window stays ≤ 7d, but make it explicit.
  - (B) `iat MUST be within ±replay_window of NOW` as an INDEPENDENT check from nonce-dedup (sentence-level addition to ADR-0109 Profile 2 validation flow).
  - (C) Carve out nonce rows in the IdempotencyStore from the 7-day default GC; use a smaller TTL like 24h that still > 300s replay window.
  - (D) `nonce_invalidation_on_key_revoke: true` policy in ADR-0111 Part C.
- **Recommended**: (B) + (D). (B) makes the replay-window an iat-vs-now invariant independent of nonce TTL. (D) closes the post-rotation replay window for compromised keys.
- **Wave**: ADR-0109 + ADR-0111 amendment (rc35 follow-up).
- **Tracked-at**: `architecture-status.yaml#s2c_ingress_identity_proof` row (need to add) + ADR-0114 δ.13 inline note.

## Residual risks observed (no action this wave)

- **ADR vocabulary expansion**: ADR-0112's `supersedes_partial:` is the first instance of an extended ADR-relationship vocabulary. Future ADRs may introduce `extends_partial`, `relates_to_strongly`, `complements`, etc. Recommended W2 follow-up: ADR yaml frontmatter schema enforcement under `gate/check_architecture_sync.sh` so unknown top-level keys fail at PR time, not at silent-graph-drift time.
- **Forward-pointing reference incubation**: the rc33+rc34 forward-pointing-reference pattern (status-yaml row asserts a doc that rc<N+1> has to author) is documented as a recurrence under F-cross-authority-agreement in `recurring-defect-families.yaml`. Stricter W2 discipline: gate rule asserting `pending_files: [...]` block in capability rows drains within N waves.
- **Hook-chain × value-based-yield × @SafetyCritical interaction**: ADR-0110 Part 2 + ADR-0112 + ADR-0113 Part C interact in undefined ways when a `Mono<StateDelta>` with `interruptSignal != null` flows through a BEFORE_LLM_INVOCATION hook and a `@SafetyCritical` hook returns Fail. Ordering relative to value-yields not specified in any ADR. Recommended: amendment to ADR-0113 or new ADR codifying interaction matrix.

## Testing gaps observed

| Gap | Test pattern needed | Wave |
|---|---|---|
| No assertion that every `docs/adr/*.yaml` top-level field is in a known-vocabulary set | Add schema-validation to `gate/check_architecture_sync.sh` reading `gate/adr-frontmatter-schema.yaml` | W2 |
| ADR `references:` text naming a file path does not assert the file resolves | grep `docs/[^\s]+\.(md\|yaml)` from each ADR `references:` block + stat each | W2 |
| `RunController.cancel()` + `get()` tenant-mismatch branches have no test asserting WARN+ log emission | Add Logback `AppenderAttachment` capture in `RunControllerCancelMismatchAuditTest` | W1 (alongside ADV-02 implementation) |
| No integration test exercising a `Task` entity creation end-to-end | `TaskLifecycleIT` exercising create + project + a2aState transition + load via TaskStateStore | W2 |
| ADR-0110 hash-chain blocking-write behaviour has no chaos test | "tamper one row, observe whether tenant is locked out + other tenants unaffected" — `AuditChainTamperIsolationIT` | W3 (alongside hash-chain impl) |

## Audit trail

- Adversarial review executed by `compound-engineering:review:adversarial-reviewer` agent on 2026-05-23.
- Findings reviewed by Claude (Opus 4.7); curated and triaged into closure / deferral / residual buckets.
- 6 of 10 findings closed in rc34 wave (this file + ADR edits + Java javadoc edit + graph builder edit + rule scope widening + enforcer text retarget).
- 4 of 10 findings deferred with named wave + decision-point + recommended remedy.
- 3 residual risks logged without immediate action.
- 5 testing gaps logged with wave-anchored remediation pattern.

This file is the durable record of the rc34 adversarial sweep. Future reviewers reading this should find every observed defect either (a) closed and pointed to a fix commit, (b) named with a wave + decision-point, or (c) logged as residual with explicit no-action rationale.

## Composes with

- `/review-mode` — the phase contract that drove this audit.
- `/design-mode` — for the four deferred follow-ups (ADV-03, ADV-06, ADV-09, ADV-10) when their waves land.

## References

- `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal-response.en.md` — sibling reviewer-facing response.
- `docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2-response.en.md` — combined R1+R2 + L1-proposals closure.
- `docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml` (rc34 W1 direction clarification added).
- `docs/adr/0109-s2c-and-ingress-server-identity-proof.yaml` (rc34 key-distribution.yaml follow-up tag added).
- `docs/adr/0111-sandbox-routing-vault-rotation-otlp-tenant-outbox-replay.yaml` (rc34 W2/W3 follow-up tags added).
- `docs/adr/0112-engine-stateless-executor-value-based-yield.yaml` (rc34 current-vs-forward state clarified).
- `docs/adr/0114-implementation-feasibility-batched-closures.yaml` (rc34 phantom-doc follow-up tags added).
- `docs/adr/0115-agent-service-l1-expansion-acceptance.yaml` (rc34 dual-track-routing-policy.yaml follow-up tag added).
- `docs/governance/rules/rule-R-C.2.md` (rc34 scope widening to service/task/** + service/session/**).
- `docs/governance/enforcers.yaml` E105 (rc34 path retargeted from dissolved agent-runtime-core).
- `gate/build_architecture_graph.py` (rc34 `supersedes_partial:` branch added).
- `agent-service/src/main/java/com/huawei/ascend/service/engine/spi/StatelessEngine.java` (rc34 javadoc forward-direction note added).
- `docs/governance/recurring-defect-families.md` (rc34 family-summary heading bumped to rc34).
