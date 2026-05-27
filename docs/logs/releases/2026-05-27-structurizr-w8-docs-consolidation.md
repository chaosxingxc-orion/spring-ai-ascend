---
level: L0
view: scenarios
status: shipped
authority: "ADR-0150 (Wave 8 architecture docs consolidation)"
---

# 2026-05-27 — Structurizr W8 Docs Consolidation Shipped

> **Historical artifact frozen at SHA `3a3208d`** (W8 merge commit on main). Baseline counts in §0 reflect the W8 snapshot (135 ADRs / 626 nodes / 1197 edges / 557 workspace elements / 398 workspace relationships). The current canonical baseline post-W1-Feature-Registry (136 ADRs / 627 nodes / 1203 edges / 566 elements / 413 relationships / 44 active engineering rules) lives in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`; see `docs/logs/releases/2026-05-27-l1-feature-registry-w1-foundation.md` for the W1 update.

**Branch:** `wave8/structurizr-first-docs-consolidation`
**PR:** #84
**Commits:** `b78191f` (W8.1-6 moves) → `ad7aee2` (W8.7-11.7) → `8096f68` (W8.12 fixes).

## 0. Canonical baselines (per Gate Rule 28)

| Metric | Value |
|---|---|
| §4 constraints | 65 |
| ADRs | 135 |
| gate rules | 143 |
| self-test cases | 260 |
| Layer-0 governing principles | 13 |
| active engineering rules | 43 |
| enforcer rows | 176 |
| Maven XML-counted tests | 461 |
| architecture graph nodes | 626 |
| architecture graph edges | 1197 |
| recurring defect families | 34 |
| workspace elements | 557 |
| workspace relationships | 398 |

These match `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`.

**Canonical baseline phrasing** (Gate Rule 28 grep):
65 §4 constraints · 135 ADRs · 143 active gate rules · 260 gate self-tests ·
13 Layer-0 governing principles · 43 active engineering rules · 176 enforcer rows ·
461 Maven XML-counted tests · 626 architecture graph nodes / 1197 edges ·
34 recurring defect families.

## 1. What W8 shipped

The architecture-design system is now physically unified under `architecture/` per ADR-0150. The user-facing surface is the 4-item layout the user named in the directive (`唯一主入口` + `docs/` + `decisions/` + `features/`):

- `architecture/workspace.dsl` — sole main entry. Carries System/Container/Component (L1 structure), Custom Element + Tags + Properties (Feature/Capability/FunctionPoint), Relationships + `saa.rel` (dependency/implements/verifies/constrains), Documentation (`!docs docs`), ADR (`!adrs decisions`), Views (4+1 organisation form).
- `architecture/docs/L1/` — canonical L1 design corpus. `agent-bus.md` / `agent-client.md` / `agent-evolve.md` / `agent-execution-engine.md` / `agent-middleware.md` / `graphmemory-starter.md` (single-narrative shape) + `agent-service/` (per-view 4+1 directory, rc55 W3-W5 + post-merge audit Wave 3).
- `architecture/decisions/` — 6 anchor ADR markdowns mirrored by `AdrMirrorCli` from `docs/adr/<id>-*.yaml` (0068, 0119, 0147, 0148, 0149, 0150).
- `architecture/features/` — optional Feature/FP DSL fragments (`capabilities.dsl` 152 entries, `function-points.dsl` 15 entries, `verification.dsl` 7 tests).

`docs/` is now the work directory for operational + governance authorities (`governance/`, `adr/`, `logs/`, `contracts/`, `dfx/`, `runbooks/`, `cross-cutting/`, `quickstart.md`, `overview.md`). It is NOT the architecture-design publish directory.

## 2. Reading path (uniform across 7 entry surfaces)

Every newcomer (human or AI) loads these 7 surfaces in order. The order matches `README.md#Reading-path`, `docs/governance/SESSION-START-CONTEXT.md#Reading-order`, and `AGENTS.md#For AI assistants — load this set` step-for-step.

1. `architecture/workspace.dsl` + `architecture/README.md` — architecture authority root.
2. `ARCHITECTURE.md` — declarative L0 system boundary + 65 §4 constraints.
3. `CLAUDE.md` — enforceable rules + Constraint↔Rule mapping.
4. `architecture/docs/L1/<module>{.md,/}` — L1 module design.
5. `docs/contracts/contract-catalog.md` — runtime promise surface.
6. `docs/quickstart.md` — operational onboarding.
7. `docs/overview.md` — narrative tour.

Each surface declares its **rhetorical stance** so readers do not conflate slices.

## 3. Rule + contract structure refresh

- **CLAUDE.md** gains `## Rhetorical stance` block + `## Constraint ↔ Rule mapping` entry-point table.
- **ARCHITECTURE.md** gains `§0.6 Rhetorical stance of this document` + `§0.7 Constraint ↔ Rule cross-reference` sections.
- **docs/contracts/contract-catalog.md** gains `## Rhetorical stance` block naming the runtime-promise slice.
- **Rule G-1.a** (kernel + card) updated: L1 path regex from `agent-*/ARCHITECTURE.md` + `docs/L2/` to `architecture/docs/L1/<module>{.md,/}` + `architecture/docs/L2/**/*.md`.

## 4. Authority chain

- **ADR-0150** (this wave) — `supersedes: [ADR-0143]`, `extends: [ADR-0147, ADR-0149]`. Locks in the user's "around Structurizr as primary entry and authority" directive.
- **`docs/logs/reviews/2026-05-27-wave-8-architecture-docs-consolidation.md`** — Rule 44 frozen-doc compliance receipt for the ARCHITECTURE.md §0.6 + §0.7 + §65 edits.

## 5. Tooling added

- `tools/architecture-workspace/.../fragment/AdrMirrorCli.java` — mirrors anchor ADRs to `architecture/decisions/` so `!adrs` resolves.
- `gate/lib/check_doc_coherence.py` — advisory cross-doc sweep (W8 Step 11.7); 11/11 PASS at this commit.

## 6. Soak window interaction

W8 lands DURING the W5 14-day soak (2026-05-27 → 2026-06-10) because it does NOT change authority direction (W5 already did that). W6 sub-wave soak schedule is unchanged: W6.a..W6.d each 15 days; W7 retirements eligible ~2026-07-25.

## 7. Tests / Gates

- `bash gate/check_architecture_workspace.sh` (BLOCKING) — PASS
- `python3 gate/lib/check_template_render_idempotency.py` — 25/25 PASS
- `python3 gate/lib/check_workspace_fragment_idempotency.py` — 7/7 byte-identical
- `python3 gate/lib/check_doc_coherence.py` — 11/11 PASS
- `bash gate/check_parallel.sh` — only `whitebox_quality_reports` remaining (4 modules × 3 reports, populated by CI's `./mvnw -Pquality verify`)
- CI `Maven build + integration tests` + `Quickstart smoke (/v1/health)` — to land on PR #84.

## 8. Cross-references

- ADR-0147 — Structurizr Workspace Authority (W0 wave; declared workspace.dsl as authority).
- ADR-0148 — Wave 0 spike PASS evidence.
- ADR-0149 — W0-W5 shipped record (W7 closure).
- ADR-0150 — this wave's authority.
- docs/logs/releases/2026-05-27-structurizr-workspace-authority-w0-w5.md — prior W0-W5 release note (frozen at SHA 825a8e2 per the historical marker added here).
- D:\.claude\plans\structurizr-d-chao-workspace-spring-ai-adaptive-hellman.md — full migration plan (Wave 8 section at the bottom).
