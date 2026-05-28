---
rule_id: R-A
title: "Business/Platform Decoupling Enforcement"
level: L0
view: development
principle_ref: P-A
authority_refs: [ADR-0064]
enforcer_refs: [E48, E49]
status: active
product_claim: "PC-001"
scope_phase: design
kernel_cap: 8
kernel: |
  **Platform code MUST NOT contain business-specific customizations. Business and example code MUST extend the platform via SPI + `@ConfigurationProperties` only — never by patching `*.impl.*` or `com.huawei.ascend.service.platform..`. The platform MUST ship a runnable quickstart (`docs/quickstart.md`) referenced from `README.md` so a developer reaches first-agent execution without platform-team intervention.**
deferred_sub_clauses:
  - id: ".c"
    title: "Quickstart Smoke Run in CI (ACTIVATED 2026-05-18)"
    re_introduction_trigger: "first `.github/workflows/*.yml` (or sibling container-based CI workflow) that boots a Spring Boot reactor end-to-end. Fired 2026-05-18 when `.github/workflows/ci.yml` quickstart-smoke job landed."
    deferred_body: |
      **Activated 2026-05-18 per Wave 4 Track E.** See `docs/governance/rules/rule-29c.md` for
      the active rule card. The original deferred draft below is preserved for audit.

      **Re-introduction trigger (original)**: first `.github/workflows/*.yml` (or sibling container-based CI
      workflow) that boots a Spring Boot reactor end-to-end. Fired 2026-05-18 when
      `.github/workflows/ci.yml` quickstart-smoke job landed.

      **Rule (now active)**: A CI job MUST execute the `docs/quickstart.md` instructions on a clean
      container and assert that `GET /v1/health` returns 200 within 60 s of `spring-boot:run` start.
      Failure of this job is a ship-blocking finding under Rule D-5.

      Composes with: ARCHITECTURE.md §4 #60; ADR-0064; Rule R-A.
    relates_to: ["ADR-0064", "Rule R-A", "ARCHITECTURE.md §4 #60"]
---

## Motivation

Rule R-A is the in-repo enforceable expression of governing principle P-A (Business / Platform Decoupling + Developer Self-Service). Once any business-specific customization lands in platform code, every downstream tenant becomes a constraint on platform evolution; once any extension requires platform-team intervention, the platform stops being self-service. The two structural conditions — SPI-only extension and a runnable quickstart referenced from README — make decoupling and self-service simultaneously testable.

## Details

Enforced by E48 (`SpiPurityGeneralizedArchTest`) and Rule R-A.c (`quickstart_present`; CI quickstart smoke job per ADR-0064 + W4 activation).

## Cross-references

- ADR-0064 — origin decision record.
- P-A — governing principle Rule R-A operationalises.
- Architecture reference: §4 #60.
- Deferred sub-clause 29.c — quickstart smoke-run in CI (W1 trigger).
- Rule R-D sub-clause .a (SPI + DFX + TCK Co-Design) — co-enforced by E48 on the SPI purity side.

## Deferred sub-clauses

Rule R-A.c (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.
