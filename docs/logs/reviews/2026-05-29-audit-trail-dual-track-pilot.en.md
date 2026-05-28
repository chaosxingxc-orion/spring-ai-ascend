---
affects_level: L1
affects_view: [logical, scenarios]
proposal_status: pilot-record
authors: ["Claude (autonomous EngineeringFrame execution)"]
related_sources:
  - docs/adr/0157-engineering-frame-ontology.yaml
  - architecture/docs/L1/engineering-frames.md
  - architecture/features/engineering-frames.dsl
  - docs/contracts/audit-trail.v1.yaml
  - docs/contracts/iam-bridge.v1.yaml
  - docs/contracts/cost-governance.v1.yaml
  - product/claims.yaml
---

# Pilot — Audit-Trail demand through the dual-track loop (PC-003)

This is the **P5 pilot** required by ADR-0157: drive one real demand through the
dual-track operating model end-to-end to prove the model survives a real
decomposition cycle before its integrity rule is promoted to blocking. The repo
is in DESIGN phase, so this pilot is a **design-state walkthrough** (model +
contracts), not a runtime build.

## Demand

Financial-grade **immutable audit trail**: every tenant-scoped Run lifecycle
event (create / state-transition / cancel / suspend-resume) and every identity /
cost decision must produce a tamper-evident, tenant-scoped, exportable audit
record. Serves **ProductClaim PC-003** (production-grade operation / governance).

## Track B walkthrough (demand response; structure-preserving)

1. **Customer demand → ProductClaim.** Resolves to PC-003 (in `product/claims.yaml`). In scope.
2. **Product Feature.** A new value-thread `Feature: Immutable Audit Trail`
   (value axis; would carry `saa.productClaim "PC-003"`). It is NOT a structural
   node — it `requires` FunctionPoints and `traverses` EngineeringFrames.
3. **FeatureSlice (iteration 1).** Audit-event capture on the Run lifecycle +
   tenant-scoped export.
4. **FunctionPoint selection (existing).** The lifecycle behaviours already exist
   as FunctionPoints anchored to frames — the slice *requires* them:
   - `FP-CREATE-RUN`, `FP-GET-RUN-STATUS` → anchored to `EF-ACCESS-ADMISSION`.
   - `FP-RUN-STATE-TRANSITION` → anchored to `EF-SESSION-TASK-STATE`.
   - `FP-CANCEL-RUN`, `FP-SUSPEND-RESUME` → anchored to `EF-TASK-CONTROL`.
   The audit Feature `traverses` `EF-ACCESS-ADMISSION`, `EF-SESSION-TASK-STATE`,
   `EF-TASK-CONTROL` — it threads across the structural map without owning it.
5. **FunctionPoint addition (new, design-only).** Audit capture itself is a new
   behaviour: `FP-AUDIT-EVENT-CAPTURE` (emit immutable audit record on each
   lifecycle transition) and `FP-AUDIT-EXPORT` (tenant-scoped export).
6. **Anchoring — and the escalation.** Where do the new FunctionPoints anchor?
   No existing frame owns "immutable audit / compliance evidence":
   - `EF-ACCESS-ADMISSION` owns ingress/identity, not audit persistence.
   - `EF-SESSION-TASK-STATE` owns Run state, not tamper-evidence.
   Per the dual-track invariant, **Track B may not mint a frame**. The demand
   *cannot land on an existing frame* → **STOP and escalate to Track A (ADR).**
7. **Track A escalation.** A follow-up ADR would introduce
   `EF-AUDIT-COMPLIANCE` (agent-service) anchoring `FP-AUDIT-EVENT-CAPTURE` /
   `FP-AUDIT-EXPORT`, specified by the already-design-only contracts
   `audit-trail.v1.yaml`, `iam-bridge.v1.yaml`, `cost-governance.v1.yaml`.

## What the pilot proves

- The dual-track decomposition is **expressible**: a real demand maps cleanly to
  ProductClaim → Feature → FeatureSlice → (existing + new) FunctionPoints →
  frames → contracts.
- The **escalation branch fires correctly**: audit persistence needs new
  structure (`EF-AUDIT-COMPLIANCE`), so the demand escalates to an ADR rather
  than silently reshaping the architecture from the demand side. This is exactly
  the invariant ADR-0157 exists to enforce.
- Existing frames absorb the lifecycle-event portion with **no structural
  change** — only `FP-AUDIT-EVENT-CAPTURE` is genuinely new.

## Outcome / lessons

- Model holds. No revision to the frame map or the operating loops is required.
- Recommended next product step (NOT done here — needs product owner): author the
  `EF-AUDIT-COMPLIANCE` Track-A ADR + the audit Feature/FunctionPoints, then
  implement behind the existing design-only contracts.
- This pilot satisfies ADR-0157's "survive one real decomposition cycle"
  precondition for promoting the EngineeringFrame-integrity rule (Rule G-22)
  from advisory to blocking after its soak.
