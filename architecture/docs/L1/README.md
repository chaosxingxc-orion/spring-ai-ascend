---
level: L1
view: scenarios
status: active
authority: "ADR-0147 + ADR-0150 (Wave 8 docs consolidation)"
---

# architecture/docs/L1/ — L1 Module Design Index

This directory holds the **human-readable** L1 architecture narrative for every active Maven module. The machine-readable structure lives in [`../../workspace.dsl`](../../workspace.dsl) (the architecture authority root per Rule G-1.b).

## Rhetorical stance

L1 docs here describe each module's **design** (responsibilities, boundary contracts, SPI surfaces, key invariants). They do NOT carry:

- Enforceable rules → those live in [`../../../CLAUDE.md`](../../../CLAUDE.md) (kernel) + [`../../../docs/governance/rules/`](../../../docs/governance/rules/) (full cards).
- Numbered architectural constraints (§4 #1..#65) → those live in [`../L0/ARCHITECTURE.md`](../L0/ARCHITECTURE.md) (root, L0 declarative).
- Runtime contracts (engine envelope, hooks, S2C callback, OpenAPI) → those live in [`../../../docs/contracts/`](../../../docs/contracts/).
- Per-capability shipped/deferred ledger → that lives in [`../../../docs/governance/architecture-status.yaml`](../../../docs/governance/architecture-status.yaml).

Read this directory for "what is this module designed to do?" Read the surfaces above for "what rule does this enforce?", "what does it promise at runtime?", "what is shipped?", and "what is the constraint corpus?".

## Module entries

| Module | L1 entry | Plane | Status |
|---|---|---|---|
| `agent-bus` | [`agent-bus/`](agent-bus/README.md) | Bus & State Hub | active |
| `agent-runtime` | [`agent-runtime/`](agent-runtime/README.md) | Compute & Control | active (run-owning runtime) |
| `agent-service` | [`agent-service/`](agent-service/) | Compute & Control | active (rc55 4+1 per-view) |
| `spring-ai-ascend-dependencies` | (no L1 design — BoM) | (build-time) | active |

## Per-module entry convention

- **`<module>/` directory** — the current canonical shape for L1 module design. Mature modules may contain the full 4+1 file set; smaller modules may keep most content in `README.md` and `ARCHITECTURE.md`.

A module is free to expand its directory with additional per-view files when its L1 design matures; the expansion is its own commit and the path change updates `module-metadata.yaml#architecture_doc` when needed.

## Adding a new module L1 design

1. Add the Maven module under repo root with a populated `<module>/module-metadata.yaml`.
2. Create `architecture/docs/L1/<module>/README.md` plus `ARCHITECTURE.md` or per-view files as needed. Declare `level: L1, view: ..., status: ...` front-matter.
3. Update `<module>/module-metadata.yaml#architecture_doc:` to point at `architecture/docs/L1/<module>/README.md` unless a module-specific companion file is intentionally more appropriate.
4. Add a row to this index above.
5. Add a `container` declaration to [`../../workspace.dsl`](../../workspace.dsl) with `saa.id MOD-<MODULE-UPPER>`.
6. Re-emit `architecture/generated/modules.dsl` via `AllFragmentsCli`; verify the workspace gate PASS.

## Cross-references

- Architecture authority root: [`../../workspace.dsl`](../../workspace.dsl).
- Workspace navigation: [`../../README.md`](../../README.md).
- **Structural axis (read before features)**: [`engineering-frames.md`](engineering-frames.md) + [`../../features/engineering-frames.dsl`](../../features/engineering-frames.dsl) — `Module → EngineeringFrame → FunctionPoint` (ADR-0157).
- Feature inventory (value axis): [`../../features/function-points.dsl`](../../features/function-points.dsl).
- Capability registry: [`../../features/capabilities.dsl`](../../features/capabilities.dsl).
- ADR corpus index: [`../../../docs/adr/INDEX.md`](../../../docs/adr/INDEX.md).
- ADR-0147 (workspace authority): [`../../../docs/adr/0147-structurizr-workspace-authority.yaml`](../../../docs/adr/0147-structurizr-workspace-authority.yaml).
- ADR-0150 (this layout consolidation): [`../../../docs/adr/0150-architecture-design-system-unification-under-structurizr.yaml`](../../../docs/adr/0150-architecture-design-system-unification-under-structurizr.yaml).
