---
rule_id: R-C.1
title: "Independent Module Evolution"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0066, ADR-0068, ADR-0094]
enforcer_refs: [E31]
status: active
product_claim: "PC-003"
scope_phase: design
kernel_cap: 6
scope_surfaces:
  - "<module>/pom.xml"
  - "<module>/module-metadata.yaml"
  - root pom.xml modules list
kernel: |
  **Every Maven module declares a sibling `module-metadata.yaml` with `module`, `kind ∈ {platform|domain|starter|bom|sample}`, `version`, `semver_compatibility`, `architecture_doc`, `dfx_doc`, `spi_packages`, `allowed/forbidden_dependencies`; each builds and tests in isolation via `mvn -pl <module> -am test`. Inter-module dependency direction is governed by the dependency-allowlist enforcer (E1).**
deferred_sub_clauses:
  - id: ".a"
    title: "Runtime Semver Compatibility Enforcement [Deferred to W2]"
    re_introduction_trigger: "first BoM release that drops a previously-published artifact, OR first starter that introduces a breaking config change without a major-version bump (target: W2)."
    deferred_body: |
      **Renamed 2026-05-21 (rc17 per ADR-0094)** — was `Rule R-C.b.b` (sub-clause .b of original R-C.b "Independent Module Evolution"). After the rc17 R-C split, the parent sub-clause `R-C.b` became standalone `Rule R-C.1`; this deferred clause renumbers from `R-C.b.b` → `R-C.1.a` accordingly.

      **Rule (draft)**: A gate rule MUST cross-check `<module>/module-metadata.yaml`'s `semver_compatibility` against the artifact's actual API delta. A starter that introduces a breaking config change without a major-version bump → gate failure. A BoM revision that removes a coordinate without a deprecation window declared in `module-metadata.yaml` → gate failure.

      Composes with: ARCHITECTURE.md §4 #62; ADR-0066; Rule R-C.1 (Independent Module Evolution); ADR-0094 (rc17 split authority).
    relates_to: ["ADR-0066", "ADR-0094", "Rule R-C.1", "ARCHITECTURE.md §4 #62"]
---

# Rule R-C.1 — Independent Module Evolution

Split out from Rule R-C.b in the rc17 wave (per ADR-0094) to separate the
**module-evolution invariant** (this rule) from the **code-as-contract
invariant** (Rule R-C) and the **run-spine invariant** (Rule R-C.2).
Originally added as legacy Rule 31 / Rule R-C.b.

## Motivation

P-C (Code-as-Everything, Rapid Evolution, Independent Modules) requires
that each Maven module evolves on its own clock — modules ship, version,
and upgrade independently. The structural enforcement is the
`module-metadata.yaml` sibling file declaring identity, semver
compatibility, SPI packages, and dependency allow/forbid lists.

## What the rule requires

**Enforcer**: E31 (gate-script).

Every reactor module under `<module>/pom.xml` MUST own a sibling
`<module>/module-metadata.yaml` declaring:

- `module` — the artifactId
- `kind` — one of `{platform | domain | starter | bom | sample}`
- `version` — semantic version
- `semver_compatibility` — compatibility marker
- `architecture_doc` — pointer to the module's `ARCHITECTURE.md`
- `dfx_doc` — pointer to the module's `docs/dfx/<module>.yaml`
- `spi_packages` — list of `*.spi.*` packages exposed (empty list for
  non-SPI-bearing kinds like `bom`)
- `allowed_dependencies` — list of upstream modules that may be imported
- `forbidden_dependencies` — list of modules explicitly banned (often
  names deleted modules to prevent reintroduction)

Each module MUST build and test in isolation via `mvn -pl <module> -am test`.

Inter-module dependency direction is governed by Rule D-6
(`module_dep_direction`) and the dependency-allowlist enforcer E1.

## Why split from R-C

The original Rule R-C bundled three orthogonal invariants:

- R-C.a (code-as-contract): every constraint maps to ≥1 enforcer surface.
- R-C.b (module evolution): every module has metadata + builds in isolation.
- R-C.c/.d/.e (run spine): tenantId required, RunStateMachine validates
  transitions, runtime can't import platform.

These three invariants live in different domains (governance system /
build system / persistence system). Splitting R-C → R-C + R-C.1 + R-C.2
clarifies which sub-rule a finding belongs to and which reviewer should
care.

## Activation history

- 2026-05-XX (early W1) — original legacy Rule 31 / Rule R-C.b activation.
- 2026-05-21 (rc17 per ADR-0094) — extracted to standalone rule R-C.1.
  Enforcer E31 unchanged; no behaviour delta.

## Deferred sub-clauses

- **Rule R-C.1.a** — Runtime Semver Compatibility Enforcement (deferred to W2; was `Rule R-C.b.b` pre-rc17 per ADR-0094 rename). Re-introduction trigger: first BoM release that drops a previously-published artifact, OR first starter that introduces a breaking config change without a major-version bump. Body lives in `docs/CLAUDE-deferred.md`.

## Cross-references

- ADR-0066 — Independent Module Evolution authority.
- ADR-0094 — rc17 rule-consolidation authority (this split).
- Rule R-C — Code-as-Contract (sibling: governance-system invariant).
- Rule R-C.2 — Run Contract Spine (sibling: persistence-system invariant).
- Rule R-I — Five-Plane Manifest (`deployment_plane` field also lives in
  `module-metadata.yaml`).
- Rule D-6 — Posture-Aware Defaults (separate from but composes with
  semver_compatibility discipline).
