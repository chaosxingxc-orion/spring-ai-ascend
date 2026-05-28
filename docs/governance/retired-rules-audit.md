---
audit_id: PHASE7-OLD-NUMERIC-RULE-AUDIT
governance_infra: true
audit_date: 2026-05-28
auditor: spring-ai-ascend Phase 7 cleanup
purpose: input to docs/CLAUDE-deferred.md elimination decision (user directive 2026-05-28)
---

# Phase 7 Old-Numeric Rule Audit Report

**Audit context**: 14 OLD-numeric rules currently sitting in `docs/CLAUDE-deferred.md` are remnants of the pre-May-2026 "67→30 D-/R-/G-/M- ratchet" rule namespace migration (ADR-0086). This audit determines, for each, whether it (a) migrated to an alphanumeric successor rule with an existing card, (b) is obsolete and should be archived, (c) needs human review.

## Disposition table

| Rule ID | Rule Title | Re-introduction Trigger | Successor Mapping | Action Recommendation |
|---|---|---|---|---|
| Rule 7 | Resilience Must Not Mask Signals | first soft-fallback path committed (target: W2 LLM gateway) | **unclear** — mentions Rule D-7 in body; no active alphanumeric card explicitly operationalizes resilience-signal masking | escalate to W2 async-orchestrator design team; either migrate to new R-*/D-* card or retire if fallback patterns handled elsewhere |
| Rule 8 | Operator-Shape Readiness Gate | first shippable jar with a real external dependency (target: W2) | **obsolete** — W0/W1 scope capped at in-memory testing; operator-shape gate explicitly deferred to W2 async landing (trigger not fired) | archive in `docs/governance/retired-rules.md` with note: conditional reactivation if W2 async-orchestrator operator-shape gate lands |
| Rule 13 | P1 Cost-of-Use Constraints | first context-cache, cost-accounting, or small/large-model handoff (target: W3) | **unclear** — Rule R-K (Skill Capacity Matrix, active) mentions cost profiling but is incomplete for full P1 roadmap intent; no card operationalizes cache-eligibility gates | escalate: reconcile Rule 13 W3 intent vs Rule R-K W1 scope |
| Rule 14 | P3 Self-Evolution Constraints | first skill-registry, memory-compression, or knowledge-dedup (target: W3) | **obsolete** — no active rule cards address memory compression / skill-registry dedup; capability not shipped; W3 scope not yet active | delete cleanly; future W3 self-modifying-capability work may introduce new rule framework |
| Rule 15 | Streamed Handoff Mode Conformance | first Flux<T> / SSE return from Orchestrator (target: W2) | **unclear** — composes with ARCHITECTURE.md §4 #11 (streamed_handoff_mode) but no enforcer fires on backpressure/cancellation/heartbeat checks; W2 streaming not shipped | escalate: if streaming lands in W2 async-orchestrator, create new card |
| Rule 16 | Cognitive Resource Arbitration | first ResilienceContract consumer (tool/skill, not just LLM) (target: W2) | **migrated → Rule R-K** (Skill Capacity Matrix, active, L1) | **DRIFT DETECTED**: Rule R-K card lacks deferred sub-clauses (.a-.e detail on call-tree budget propagation, tenant-scoped quota enforcement); fold into Rule R-K.md YAML frontmatter `deferred_sub_clause:` block |
| Rule 17 | Degradation Authority + Resume Re-Authorization | first soft-fallback path committed (W2, composes with Rule 7) | **partially migrated** → Rule R-M.d (S2C Callback Envelope, active) **+** Rule R-J.b (Cancel re-authorization, active) | fold degradation-authority (.a) into deferred sub-clause of Rule R-M; resume-reauth (.b) already operationalized by Rule R-J.b enforcer E105/E106 |
| Rule 18 | Eval Harness Gate | first shipped capability with golden corpus + LLM-as-judge evaluator (target: W4) | **obsolete** — W4 scope not active; no enforcer rows reference eval harness corpus/thresholds gates | delete cleanly; W4 future work may introduce new eval rule framework |
| Rule 19 | Runtime Hook Conformance | first W2 LLM gateway capability (first ChatClient call in production) (target: W2) | **unclear** — mentions ARCHITECTURE.md §4 #16; Rule R-M.c covers hooks at orchestration level but not producer-client isolation | escalate: audit Rule R-M.c vs Rule 19 scope; if hook-chain conformance missing, append as deferred sub-clause |
| Rule 22 | PayloadCodec Discipline | first Checkpointer implementation persisting to durable store (W2 Postgres) (target: W2) | **unclear** — ADR-0022 authority but no active engineering rule operationalizes the codec enforcement gate; Checkpointer lives in Rule R-M.d scope | escalate: if W2 Checkpointer lands, determine whether PayloadCodec enforcement is Rule R-M.d sub-clause or separate card |
| Rule 23 | Suspension Write Atomicity Enforcement | first W2+ Orchestrator performing RunRepository.save(suspended) + Checkpointer.save(payload) pair (target: W2) | **unclear** — mentions Rule D-5 + ADRs 0024/0007; no current card operationalizes "JVM mid-write kill → post-restart consistency" integration test requirement | fold as deferred sub-clause into pending W2 Checkpointer/suspension rule |
| Rule 26 | Skill Lifecycle Conformance | first Skill SPI implementation committed (target: W2) | **partially migrated** → Rule R-L (Sandbox Permission Subsumption, active, L1) **+** ADR-0030 (Skill SPI contract) | audit Rule R-L.md for completeness: append Skill lifecycle gaps (.a init mandatory, .b suspend/resume resource release, .c teardown via try-finally, .d cost-receipt aggregation) to R-L deferred_sub_clause block |
| Rule 27 | Untrusted Skill Sandbox Mandate | first UNTRUSTED-tier Skill in research/prod posture (target: W3) | **migrated → Rule R-L** (Sandbox Permission Subsumption, active, L1) | fold posture-specific UNTRUSTED trust-tier startup gate enforcement into Rule R-L.md deferred_sub_clause; note interaction with Rule D-6 |
| Rule 28k.b | Schema↔Java-Shape Parity ArchUnit | W3 contract-design sprint kickoff (same trigger as Rule M-2.a.b) (target: W3) | **partially migrated** → Rule M-2.a (Schema-First Domain Contracts, active, L1) **+** Rule R-C.a (Code-as-Contract, active, L1) | fold into Rule M-2.a deferred_sub_clause: extend ArchUnit parity tests (E77/E78 pattern) to ALL `docs/contracts/*.v1.yaml` schema ↔ Java type pairs |

## Disposition summary

- **Migrated with active successor (5)**: Rule 16 → R-K; Rule 17 (partial) → R-J.b + R-M.d; Rule 26 (partial) → R-L; Rule 27 → R-L; Rule 28k.b (partial) → M-2.a + R-C.a
- **Drift detected (1)**: Rule 16 — R-K card incomplete on call-tree budget; needs deferred_sub_clause integration
- **Unclear / escalate (6)**: Rule 7, Rule 13, Rule 15, Rule 19, Rule 22, Rule 23
- **Obsolete / delete cleanly (3)**: Rule 8, Rule 14, Rule 18

## Phase 7 cleanup actions implied by this audit

For the 14 old-numeric entries currently in `docs/CLAUDE-deferred.md`:

1. **Drop the 3 obsolete entries** — Rule 8, 14, 18 — deleted from CLAUDE-deferred.md; no archival doc needed (designs were never shipped).
2. **Fold the 5 partially/fully migrated entries** into their successor card YAML frontmatter `deferred_sub_clauses:` block — Rules 16, 17, 26, 27, 28k.b → R-K, R-M+R-J, R-L, R-L, M-2.a (and R-C.a).
3. **Escalate the 6 unclear entries** to W2/W3 async-orchestrator design — Rules 7, 13, 15, 19, 22, 23 — record them in `docs/governance/escalations.md` with the design questions; remove from CLAUDE-deferred.md once their fate is decided.

After these three actions, CLAUDE-deferred.md is empty of OLD-numeric content. The remaining 23 alphanumeric sub-clauses (R-A.c, R-B.b, etc.) get migrated into their parent rule cards' YAML frontmatter (separate task). After both migrations land, `docs/CLAUDE-deferred.md` is deleted entirely.

## Authority

Source authority for rule namespace migration: ADR-0086 (Rule Namespace Ratchet). Original mapping in `docs/logs/rule-migration-map.yaml`. Rule cards source-of-truth: `docs/governance/rules/rule-*.md`. This audit is a one-shot Phase 7 input — not a continuing process.
