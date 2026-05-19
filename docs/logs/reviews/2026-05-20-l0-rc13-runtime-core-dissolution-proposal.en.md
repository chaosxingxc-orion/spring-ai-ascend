---
level: L0
view: scenarios
review_id: 2026-05-20-l0-rc13-runtime-core-dissolution-proposal
date: 2026-05-20
authors: ["Chao Xing"]
freeze_id: null
covers_views: [scenarios]
spans_levels: [L0]
affects_level: L0
affects_view: scenarios
affects_artefact: [ARCHITECTURE.md, CLAUDE.md, "docs/governance/architecture-status.yaml", "docs/governance/architecture-graph.yaml", "docs/governance/rules/rule-R-C.md", "docs/governance/rules/rule-R-I.md", "docs/contracts/contract-catalog.md", "docs/contracts/ingress-envelope.v1.yaml", "docs/adr/0088-agent-runtime-core-dissolution.yaml", "docs/adr/0089-edge-plane-ingress-gateway-mandate.yaml"]
related_adrs:
  - ADR-0088
  - ADR-0089
authority: "ADR-0088 (agent-runtime-core dissolution); ADR-0089 (Edge-Plane Ingress Gateway Mandate); plan-mode dialogue 2026-05-20"
---

# rc13 L0 Architecture Ratchet Proposal — Dissolve agent-runtime-core + Lock client→bus→server Ingress

## Context

The W1-russell-2026-05-14 freeze on ARCHITECTURE.md was set when the L0 module-layout reflected the 9-module reactor including the transient `agent-runtime-core` kernel-shim module (ADR-0079, 2026-05-18). Two L0 architectural defects surfaced in plan-mode dialogue (2026-05-20):

1. The 6-module L0 mental model never acknowledged `agent-runtime-core`, even though the physical reactor included it. Every rc-wave kept rediscovering ~30 active-corpus surfaces that named a module the L0 narrative did not.

2. There was no formal contract enforcing that `agent-client` (edge plane) routes through `agent-bus` (bus_state plane) to reach the compute_control plane. The agent-client SDK is skeleton today (0 java files); locking the positive topology contract NOW prevents the future W3+ SDK from picking the most-convenient HTTP path (direct to `/v1/runs`) by default.

This proposal authorises the rc13 edits to ARCHITECTURE.md under the W1-russell-2026-05-14 freeze, per Rule 44 (`frozen_doc_edit_path_compliance` / E63). The structural changes are formalised in ADR-0088 (dissolution) + ADR-0089 (ingress mandate) and audited in the rc13 release note (`docs/logs/releases/2026-05-20-l0-rc13-runtime-core-dissolution-and-ingress-mandate.en.md`).

## Proposal scope

### ARCHITECTURE.md edits

- §2 Module layout: "Nine-module post-ADR-0078 + ADR-0079 state" → "Eight-module post-ADR-0088 state". Table rows reduce from 9 to 8 (delete `agent-runtime-core`).
- §2 Tree diagram: `agent-runtime-core/` block deleted; `agent-bus/` block expanded with `bus.spi.ingress/` (NEW per ADR-0089) and `bus.spi.s2c/` (relocated per ADR-0088); `agent-execution-engine/` block expanded with `engine.orchestration.spi/` (relocated per ADR-0088 — RunMode, Checkpointer, Orchestrator, RunContext, SuspendSignal, TraceContext, ExecutorDefinition); `agent-service/runtime/` block updated to host `runs/` + `idempotency/` directly (relocated from dissolved kernel-shim module).
- Module dependency direction block: rewrite to reflect 8-module DAG. New `agent-client → bus.spi.ingress` edge documented per Rule R-I sub-clause `.b`.

### Authority surfaces

- `CLAUDE.md`: Rule R-C sub-clause `.c` path scope rewrite + Rule R-I sub-clause `.b` kernel addition.
- `docs/governance/rules/rule-R-C.md`: card frontmatter authority_refs swap ADR-0079 → ADR-0088; sub-clause `.c` body rewrite.
- `docs/governance/rules/rule-R-I.md`: card frontmatter +ADR-0089 authority + E143 enforcer; sub-clause `.b` body addition.
- `docs/governance/architecture-status.yaml`: `repository_counts.reactor_modules` 9 → 8; `internal_modules` 7 → 6; `agent_runtime_kernel.implementation` paths relocated; `baseline_metrics.adr_count` 86 → 88; `baseline_metrics.active_gate_checks` 116 → 117; `baseline_metrics.enforcer_rows` 142 → 144; `baseline_metrics.gate_executable_test_cases` 180 → 182.
- `docs/governance/architecture-graph.yaml`: regenerated post-merge by `python gate/build_architecture_graph.py --check` to reflect ADR-0088 + ADR-0089 + Rule R-I.b + E143 + E144 + Rule 105 + dissolved-module node removal + new supersedes/extends edges.
- `docs/contracts/contract-catalog.md`: §2 Active SPI table rewrite (5 ownership relocations + 1 new IngressGateway row); §2 SPI count table + Structural carriers table updated; §3 YAML domain contracts +ingress-envelope.v1.yaml row; §7 Maven BoM 9 → 8 rows.

## Rationale

The user-chosen approach captured in `D:\.claude\plans\l0-agent-runtime-core-agent-client-agen-staged-kay.md`:

- **Issue 1 — Dissolve, not extend.** Of three options (keep + widen Rule R-G/R-H scope; fold back into agent-service; redistribute to semantic homes), the user chose redistribute. `agent-runtime-core` shipped zero unique architectural intent — every type it owned had a clean semantic home. The back-dep that motivated ADR-0079 is resolved by co-locating `RunMode` with the orchestration SPI (in `agent-execution-engine`), making `Run → engine` a service→engine edge that the existing module-metadata `allowed_dependencies` already permits.
- **Issue 2 — Anchor on Rule R-I, not new top-level rule.** Of four options (new top-level rule R-N; extend Rule R-E to four-track; new sub-clause on Rule R-I; new contract YAML + SPI), the user chose Rule R-I sub-clause `.b` + new contract YAML + new SPI. R-I operationalises Principle P-I (Five-Plane Distributed Topology); a sub-clause is the natural home for "no direct edge between planes" without inflating the active rule count.
- **Symmetric bus payoff.** Co-locating S2C transport SPI in `agent-bus.bus.spi.s2c` (per ADR-0088) AND adding `bus.spi.ingress.IngressGateway` (per ADR-0089) makes `agent-bus` the single cross-plane control surface in both directions. This is the architectural payoff the user picked.

## Verification

- ADR-0088 + ADR-0089 land in `docs/adr/` with full decision narrative.
- rc13 release note at `docs/logs/releases/2026-05-20-l0-rc13-runtime-core-dissolution-and-ingress-mandate.en.md` references this proposal.
- `bash gate/check_parallel.sh` passes Rule 44 (frozen_doc_edit_path_compliance) because this proposal cites ARCHITECTURE.md under `affects_artefact:`.
- `./mvnw -T1C verify` baseline maintained post-merge.

## Related sessions

- Plan-mode dialogue 2026-05-20 (single session): `D:\.claude\plans\l0-agent-runtime-core-agent-client-agen-staged-kay.md`.
- Prior wave (rc12): `docs/logs/reviews/2026-05-19-l0-rc11-contract-authority-constraint-systematic-review.en.md`.
