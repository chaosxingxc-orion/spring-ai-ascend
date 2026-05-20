---
level: L0
view: scenarios
release_id: v2.0.0-rc13
date: 2026-05-20
authors: ["Chao Xing"]
responds_to: []
related_adrs:
  - ADR-0088
  - ADR-0089
freeze_id: null
covers_views: [scenarios]
spans_levels: [L0]
affects_artefact:
  - ARCHITECTURE.md
  - CLAUDE.md
  - docs/governance/architecture-status.yaml
  - docs/governance/rules/rule-R-C.md
  - docs/governance/rules/rule-R-I.md
  - docs/contracts/contract-catalog.md
  - docs/contracts/ingress-envelope.v1.yaml
  - docs/contracts/s2c-callback.v1.yaml
---

# v2.0.0-rc13 — L0 Architecture Ratchet: Dissolve `agent-runtime-core` + Lock `client → bus → server` Ingress

> **Historical artifact frozen at SHA 001aecc (v2.0.0-rc13 merge).** Baseline counts in this document (65 §4 constraints / 88 ADRs / 117 active gate rules / 182 gate self-tests / 30 active engineering rules / 144 enforcer rows) reflect the corpus state at rc13 merge time and are NOT retroactively updated. The current canonical baseline (post-rc15: 65 §4 / 91 ADRs / 118 gate rules / 194 self-tests / 31 engineering rules / 150 enforcer rows) is tracked in `docs/governance/architecture-status.yaml#architecture_sync_gate.allowed_claim` and the rc15 release note (`docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md`).

## Summary

rc13 is a **structural ratchet wave**, not a corrective response. The user surfaced two L0 architectural defects in plan-mode dialogue (2026-05-20):

1. The L0 mental model is **6 substantive modules**, but the physical reactor was **9** — the extra was `agent-runtime-core`, which ADR-0079 introduced (2026-05-18) to break a back-dep cycle between `agent-service` and `agent-execution-engine`. The module shipped zero unique architectural intent, and every type it owned had a clean semantic home in one of the 6 substantive modules. Rule G-2.e / Rule 94 widening surfaced ~30 active-corpus surfaces that all had to mention a module the L0 mental model did not acknowledge. rc13 dissolves it.

2. The platform had **no formal contract enforcing that `agent-client` (edge plane) routes through `agent-bus` (bus_state plane) to reach `agent-service` (compute_control plane)**. Today the SDK is skeleton (0 Java files); locking the positive topology contract NOW prevents the future SDK from picking the most-convenient HTTP path (direct to `/v1/runs`) by default. rc13 introduces the `IngressGateway` SPI in `agent-bus` and Rule R-I sub-clause `.b` to enforce it.

The two ADRs land in the same wave because they share a structural payoff: ADR-0088 relocates `S2cCallbackTransport` (server → client callback) to `agent-bus.bus.spi.s2c`; ADR-0089 adds `IngressGateway` (client → server) to `agent-bus.bus.spi.ingress`. After both ADRs, `agent-bus` owns the entirety of cross-plane traffic in both directions — the Bus & State Hub plane becomes the single cross-plane control surface, exactly what Principle P-I's five-plane isolation argument predicts.

## Four competitive pillars (Rule R-B)

- **performance** — no runtime path change at W1 (the ingress contract is `design_only`; the IngressGateway SPI ships as a stub). `./mvnw verify` remains green.
- **cost** — no LLM/model-call change; rc12 cost baseline carries forward.
- **developer_onboarding** — `docs/quickstart.md` sub-second-build path updated to `./mvnw -pl agent-execution-engine -am test -q` (the new orchestration-SPI home after dissolution); incremental-test guidance updated to reflect the 3-module split (runs+idempotency in `agent-service`; orchestration SPI in `agent-execution-engine`; s2c SPI in `agent-bus`).
- **governance** — 1 new prevention rule (105 `edge_no_direct_compute_link`) + 1 new ArchUnit enforcer (E143 `EdgeToComputeDirectLinkArchTest`) + 1 new contract YAML (`ingress-envelope.v1.yaml`, status `design_only`) + 2 new ADRs (0088 + 0089) + Rule R-I sub-clause `.b` kernel.

## Baseline metrics

Rule 28 release-note table (canonical baseline-truth row format):

| Metric | Count | Delta (rc12 → rc13) |
|---|---|---|
| §4 constraints | 65 | unchanged (#1–#65) |
| Active ADRs | 88 | +2 (ADR-0088 agent-runtime-core dissolution + ADR-0089 edge-plane ingress gateway mandate; ADR-0079 status accepted→superseded by ADR-0088) |
| Active gate rules | 117 | +1 (Rule 105 `edge_no_direct_compute_link`) |
| Gate self-test cases | 182 | +2 (rc13 adds 2 fixtures × 1 new rule) |
| Active engineering rules | 30 | unchanged head-count (rc13 adds Rule R-I sub-clause `.b` internally; sub-clause expansion of existing R-I, not a new top-level rule) |
| Enforcer rows | 144 | +2 (E143 EdgeToComputeDirectLinkArchTest + E144 gate Rule 105 source-grep) |
| Reactor modules | 8 | −1 (agent-runtime-core dissolved; was 9 at rc12) |
| Layer-0 governing principles | 13 | unchanged (P-A..P-M) |
| Maven tests green | 371 | rc12 baseline carries forward; rc13 file moves do not add/remove test classes — the 4 test files moved with their production sources (post-merge `./mvnw verify` verified 371 surefire+failsafe total per architecture-status.yaml#baseline_metrics.maven_tests_green). Note: rc14 corrective wave (2026-05-20) reconciles graph counts (363/539→376/558) and adds Rule G-8 cross-authority parity; see rc14 release note. |

## Family taxonomy

rc13 is a user-initiated ratchet wave (not a reviewer response), so there is no cited-finding family taxonomy. The structural changes decompose naturally:

| Change family | Authority | Scope |
|---|---|---|
| **D-α: Module dissolution** | ADR-0088 | Delete `agent-runtime-core/`; redistribute 16 production Java sources + 4 tests to semantic-home modules; update 7 module-metadata.yaml files; rewire ~37 cross-module imports via single pass |
| **D-β: Symmetric bus plane** | ADR-0088 + ADR-0089 | Co-locate cross-plane control surfaces in agent-bus — `bus.spi.s2c` (relocated from dissolved core) + `bus.spi.ingress` (new SPI). Pairing makes `agent-bus` the single cross-plane control surface in both directions |
| **D-γ: Contract scaffolding (design_only)** | ADR-0089 | Ship `docs/contracts/ingress-envelope.v1.yaml` with `status: design_only`; promote to `runtime_enforced` when agent-client SDK lands (W3+) |
| **D-δ: Negative invariant enforcement** | Rule R-I.b + ADR-0089 | ArchUnit `EdgeToComputeDirectLinkArchTest` (E143) + gate Rule 105 `edge_no_direct_compute_link` (E144); both vacuous-but-armed at W1 (agent-client is skeleton) |
| **D-ε: Authority surfaces** | ADR-0088 (supersedes ADR-0079) | Rule R-C sub-clause `.c` path scope updated; ARCHITECTURE.md module-layout 9→8 rows; architecture-status.yaml repository_counts updated; contract-catalog.md SPI ownership + Maven BoM tables rewritten |
| **D-ζ: Deleted-module-name scope widening** | Rule 87 + Rule 94 + Rule 98 | All three rules now scan for `agent-runtime-core` alongside `agent-platform` + `agent-runtime`. Marker vocabulary extended with `dissolution|dissolved|relocated|rc13|ADR-0088|ADR-0089` |

## Methodology (load-bearing)

rc13 follows the rc11/rc12 categorize→sweep→batch-fix→prevention discipline, but the trigger was user-directed L0 structural reframing rather than reviewer findings:

1. **Plan-mode dialogue** — surfaced two issues, used `AskUserQuestion` to align on (a) dissolve-vs-fold-vs-keep for runtime-core, (b) anchor-rule choice for ingress, (c) phasing, (d) initial enforcement level. User decisions captured in `D:\.claude\plans\l0-agent-runtime-core-agent-client-agen-staged-kay.md`.
2. **Back-dep prevention check** — read every Java file in agent-runtime-core to map cross-package imports BEFORE moving files; found `RunMode` was imported by both `runs/` (Run.java) and `orchestration.spi/` (SuspendSignal, RunContext). Co-located RunMode with `engine.orchestration.spi` to break the back-dep cleanly. (This was the exact root cause ADR-0079 created agent-runtime-core for; ADR-0088 solves it via semantic co-location instead of a kernel-shim module.)
3. **Atomic single-pass migration** — Python script (`scripts/rc13_dissolve_runtime_core.py`) moves 20 files + rewrites ~37 cross-module imports in one pass, using `'rb' + 'wb' + b'\\r\\n' → b'\\n'` normalization per the cross-platform CRLF lesson from rc12 K-δ.
4. **Symmetric bus mandate landed atomically** — the ADR-0088 s2c move + ADR-0089 ingress mandate share a single PR so the "bus owns cross-plane in both directions" payoff is visible from the first review.
5. **Three-wave conceptual structure, single PR execution** — Wave 1 (architecture + rules + contracts), Wave 2 (SPI stubs + enforcement), Wave 3 (pre-positioned for SDK landing) — all delivered in one atomic PR per user direction (plan-mode 2026-05-20).
6. **Sweep-then-verify** — initial gate run after the structural moves surfaced 10 rule failures including stale DFX spi_packages, contract-catalog drift, and frozen-doc edit citation. All resolved in the same wave.

## Verification

- `./mvnw -T1C clean verify` — BUILD SUCCESS across 8 reactor modules; 371 surefire+failsafe tests green (rc12 baseline carried forward; rc13 file moves preserved test count — the 4 test files moved with their production sources). rc14 corrective wave reconciles graph counts (363/539→376/558) and adds Rule G-8 cross-authority parity per ADR-0090.
- `bash gate/check_parallel.sh` — `executed 117 rules; all PASS` (rc12 + Rule 105).
- `bash gate/test_architecture_sync_gate.sh` — `Tests passed: 182/182`.
- `python gate/build_architecture_graph.py --check` — idempotent regeneration; live node/edge counts written back to `architecture-status.yaml#baseline_metrics`.
- Negative-fixture spot-check for Rule 105 (Rule 105 test fixture) — temp-inject a forbidden compute_control import in `agent-client/`, confirm Rule 105 fails closed.

## ADRs landed this wave

- **ADR-0088** — agent-runtime-core dissolution; redistribute kernel SPI to semantic-home modules; align L0 reactor with 6-module narrative. Supersedes ADR-0079.
- **ADR-0089** — Edge-Plane Ingress Gateway Mandate; `client → bus → server` is the only allowed C2S topology. Extends ADR-0049, ADR-0050, ADR-0069. Contract status `design_only` at W1.

## Files deleted this wave

- `agent-runtime-core/` (entire directory: pom.xml, module-metadata.yaml, ARCHITECTURE.md, src/, target/).
- `docs/dfx/agent-runtime-core.yaml`.

## Out of scope (deferred)

- `IngressGateway` runtime implementation (HTTP-to-bus dispatcher) — deferred to W3+ per ADR-0089 `deferred_runtime_binding.trigger` (first agent-client SDK release).
- agent-client SDK Java implementation — ADR-0049 deferral unchanged.
- Bus physical-channel mapping for the ingress flow — Rule R-E.4 / W2 deferral.
- Promotion of `ingress-envelope.v1.yaml` from `design_only` to `runtime_enforced` — same trigger as `IngressGateway` impl.
- Cosmetic rename of `ascend.springai.service.runtime.{runs,idempotency}` → `ascend.springai.runs` for package boundary cleanliness — W3+ cosmetic; out of scope here.

## References

- Plan: `D:\.claude\plans\l0-agent-runtime-core-agent-client-agen-staged-kay.md`.
- ADR-0088: `docs/adr/0088-agent-runtime-core-dissolution.yaml`.
- ADR-0089: `docs/adr/0089-edge-plane-ingress-gateway-mandate.yaml`.
- Rule R-I.b card: `docs/governance/rules/rule-R-I.md`.
- Rule R-C.c path update: `docs/governance/rules/rule-R-C.md`.
- Rule history: `docs/governance/rule-history.md`.
- Migration script: `scripts/rc13_dissolve_runtime_core.py`.
- L1 ARCH sweep script: `scripts/rc13_arch_sweep.py`.
