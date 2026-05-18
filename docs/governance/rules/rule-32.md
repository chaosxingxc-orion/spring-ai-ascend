---
rule_id: 32
title: "SPI + DFX + TCK Co-Design"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0067]
enforcer_refs: [E48, E53, E54]
status: active
kernel_cap: 8
kernel: |
  **Every module declared `kind: domain` in `module-metadata.yaml` MUST expose at least one `*.spi.*` package containing ≥ 1 public interface, listed under `spi_packages:`. Every module with `kind: platform` or `kind: domain` MUST publish a `docs/dfx/<module>.yaml` covering five DFX dimensions — `releasability`, `resilience`, `availability`, `vulnerability`, `observability` — each with a non-empty body. The sibling `<module>-tck` reactor module and conformance suite are deferred per `CLAUDE-deferred.md` 32.b / 32.c (W2 trigger).**
---

## Motivation

Rule 32 is the in-repo enforceable expression of governing principle P-D (SPI-Aligned, DFX-Explicit, Spec-Driven, TCK-Tested). Domain modules without an SPI become customisation-by-source-patch waiting to happen; platform/domain modules without a DFX manifest ship resilience and availability claims as marketing rather than commitments. The TCK companion module is deferred to W2 but the SPI surface and the DFX manifest land in the same PR that declares a module `kind: domain` or `kind: platform`.

## Details

Enforced by E48 (`SpiPurityGeneralizedArchTest`), Gate Rule 35 (`dfx_yaml_present_and_wellformed`), and Gate Rule 36 (`domain_module_has_spi_package`).

## Cross-references

- ADR-0067 — origin decision record.
- P-D — governing principle Rule 32 operationalises.
- Architecture reference: §4 #63.
- Deferred sub-clauses 32.b (TCK module scaffolding), 32.c (TCK conformance content), 32.d (vulnerability-scanner integration).
- Rule 29 (Business/Platform Decoupling Enforcement) — co-enforced by E48 on the SPI purity side.
- Rule 31 (Independent Module Evolution) — the `module-metadata.yaml` that declares `kind: domain` is the same artefact Rule 32 reads.

## Deferred sub-clauses

Rule 32.b, Rule 32.c, Rule 32.d (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule 96 (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
