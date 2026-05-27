# AGENTS.md

## Language Rule

**Translate all instructions into English before any model call.** Never pass Chinese, Japanese, or other non-English text into an LLM prompt, tool argument, or task goal.

---

## Authoritative Sources (read these first)

This file is intentionally a **thin operational wrapper** for Codex / autonomous-agent harnesses. It does NOT carry the rule inventory or any baseline counts. The single sources of truth are:

| Topic | Authoritative file |
|---|---|
| Layer-0 governing principles (P-A..P-M) + Layer-1 engineering rules (active + deferred) | [`CLAUDE.md`](CLAUDE.md) |
| **Architecture authoring root (W5+ per ADR-0147)** | [`architecture/workspace.dsl`](architecture/workspace.dsl) + [`architecture/README.md`](architecture/README.md) — Structurizr DSL workspace closure (profile/features/docs/decisions/generated/views). L1 feature/function-point inventory at `architecture/features/`. |
| Per-capability shipped / deferred ledger; baseline counts (rules, ADRs, tests, gate rules, self-tests, nodes, edges) | [`docs/governance/architecture-status.yaml`](docs/governance/architecture-status.yaml) (the `architecture_sync_gate.allowed_claim` field is the canonical baseline; `#capabilities` authority migrates to `architecture/features/capabilities.dsl` at W6 yaml sunset) |
| Deferred sub-clauses with re-introduction triggers | [`docs/CLAUDE-deferred.md`](docs/CLAUDE-deferred.md) |
| ADRs (decision corpus) | [`docs/adr/`](docs/adr/) (every active rule cites its authority ADR) |
| Quickstart for new-agent onboarding | [`docs/quickstart.md`](docs/quickstart.md) |

**Why this is a thin wrapper:** prior versions of AGENTS.md carried an "Eleven active rules" tagline that was authored when CLAUDE.md held an 11-rule subset. CLAUDE.md has since grown well beyond that subset (current rule + principle counts live in `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`), while AGENTS.md was never regenerated. The v2.0.0-rc3 cross-constraint audit (P1-1 / β-6 / γ-1) and the v2.0.0-rc4 follow-up review P1-1 both flagged count drift across AGENTS / README / architecture-status as a defect family. The structural fix — applied here — is to stop carrying any baseline counts in AGENTS.md so the canonical source can evolve without dragging this file along.

---

## For AI assistants — load this set

A coding agent or LLM session reaches an **unbiased** architecture picture by loading these 7 surfaces in order. The order matches `README.md#Reading-path` step-for-step. Loading any one in isolation produces a partial view.

1. `architecture/workspace.dsl` + `architecture/README.md` — architecture authority root (Structurizr DSL workspace + closure navigation; ADR-0147 + ADR-0150).
2. `ARCHITECTURE.md` — declarative L0 system boundary + 65 §4 architectural constraints.
3. `CLAUDE.md` — enforceable Layer-0 principles + Layer-1 rules (each rule cites the §4 constraint it enforces).
4. `architecture/docs/L1/README.md` + `architecture/docs/L1/<module>{.md,/}` (for the module you touch) — L1 module design.
5. `docs/contracts/contract-catalog.md` — runtime promise surface (wire shapes, route behavior, SPI signatures).
6. `docs/quickstart.md` — operational onboarding (boot, post `POST /v1/runs`, observe).
7. `docs/overview.md` — narrative tour (after-the-fact prose).

## Rhetorical stance of each top-level doc

| Surface | Slice | What it carries | What it does NOT carry |
|---|---|---|---|
| `architecture/workspace.dsl` | machine-readable architecture model | structure + features + relationships + views + !docs + !adrs | enforcement, runtime promises, operational onboarding |
| `ARCHITECTURE.md` | declarative L0 constraints | 65 §4 numbered architectural constraints + system boundary | enforcement, runtime contracts, L1 module design |
| `CLAUDE.md` | enforceable rules | rule kernels + Layer-0 principles + Constraint↔Rule mapping | architecture (workspace.dsl owns it), constraints (ARCHITECTURE.md owns them) |
| `docs/contracts/contract-catalog.md` | runtime promise surface | HTTP API + SPI + envelopes + OpenAPI + each contract's authority ADR + enforcer | declarative constraints, enforcement rules |
| `architecture/docs/L1/<module>{.md,/}` | L1 module design | how this module realises its slice of the constraints | constraints themselves, rules, runtime contracts |
| `docs/quickstart.md` | operational onboarding | boot + first-run walkthrough | architecture, rules, contracts |
| `docs/overview.md` | narrative tour | after-the-fact prose for non-architecture readers | authority, enforcement, runtime contracts |

This separation prevents an AI session from conflating "the architecture" with any single surface. The 7 surfaces are distinct slices; loading them together produces the unbiased picture.

---

## Operational Conventions for Autonomous Agents

The following conventions are stable across rule-count changes and apply to every coding agent loaded into this repo:

1. **Before writing any code or plan**, follow Rule D-1 in CLAUDE.md (Root-Cause + Strongest-Interpretation). Surface assumptions; name the root cause in one sentence with `file:line` evidence; pick the strongest valid reading of the requirement.
2. **Before every commit**, run the Rule D-3.a Pre-Commit Checklist (contract truth · orphan config · error visibility · lint green · test honesty).
3. **For UI / frontend changes**, drive the feature through a real browser before declaring done (Rule from `Doing tasks` section).
4. **For Java verification**, use `./mvnw clean verify` not `./mvnw test` — the latter skips `*IT.java` (Failsafe) tests. This is a recurring trap; the v2.0.0-rc1 post-release wave landed 4 IT regressions because `test` was used.
5. **For gate verification**, use `bash gate/check_architecture_sync.sh` (canonical). The PowerShell entrypoint `gate/check_architecture_sync.ps1` was deprecated in v2.0.0-rc2 — it now exits 2 with a `DEPRECATED` banner.
6. **For any architecture decision**, walk the ADR corpus first (`docs/adr/0001-...yaml` through the highest-numbered ADR). Each rule in CLAUDE.md cites its authority ADR.
7. **For pull request bodies**, follow the format in `compound-engineering:git-commit-push-pr` skill if available, OR replicate the prior commit's HEREDOC + `Co-Authored-By` line.

---

## When to Update This File

AGENTS.md should change ONLY when:

- A new authoritative source is added (e.g., a new top-level corpus document) and needs to be listed in the "Authoritative Sources" table above.
- An operational convention changes that materially affects agent loop behavior (e.g., a new mandatory verification command).
- The thin-wrapper posture itself is being revisited.

AGENTS.md should **NOT** change when:

- A new engineering rule is added to CLAUDE.md (the count lives there).
- A new ADR is published (the corpus lives in `docs/adr/`).
- A baseline count moves (the canonical claim lives in `architecture-status.yaml`).
