---
rule_id: 31
title: "Independent Module Evolution"
level: L0
view: development
principle_ref: P-C
authority_refs: [ADR-0066]
enforcer_refs: [E52]
status: active
kernel_cap: 8
kernel: |
  **Every reactor module under `<module>/pom.xml` MUST own a sibling `<module>/module-metadata.yaml` declaring `module`, `kind ∈ {platform | domain | starter | bom | sample}`, `version`, and `semver_compatibility`. Each module MUST build and test in isolation via `mvn -pl <module> -am test`. Inter-module dependency direction is governed by Rule 10 (`module_dep_direction`).**
---

## Motivation

Rule 31 is the in-repo enforceable expression of governing principle P-C (Code-as-Everything, Rapid Evolution, Independent Modules). Independent module evolution requires that each module can declare its kind, version, and semver-compatibility contract without leaking those details into a central registry. Combined with the build-in-isolation predicate, this lets a downstream consumer upgrade one module without forcing a coordinated upgrade of all six.

## Details

Enforced by Gate Rule 34 (`module_metadata_present_and_complete`) and existing Gate Rule 10.

## Cross-references

- ADR-0066 — origin decision record.
- P-C — governing principle Rule 31 operationalises (alongside Rule 28).
- Architecture reference: §4 #62.
- Deferred sub-clause 31.b — runtime semver compatibility enforcement (W2 trigger).
- Rule 39 (Five-Plane Manifest) — `deployment_plane` is declared in the same `module-metadata.yaml`.
- Rule 32 (SPI + DFX + TCK Co-Design) — `kind: domain` modules carry additional SPI/DFX obligations.

## Deferred sub-clauses

Rule 31.b (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
