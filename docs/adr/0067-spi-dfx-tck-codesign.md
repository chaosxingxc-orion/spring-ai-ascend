# ADR-0067 — SPI + DFX + TCK Co-Design

- Status: Accepted (SPI + DFX active; TCK scaffolding deferred to W2)
- Date: 2026-05-14
- Authority: User directive — "Architecture design must strictly co-design with SPI; every module must have an SPI. Platform must be releasable, resilient, available, vulnerability-detected, DFX-clarified, spec-driven, test-driven (TCK)."
- Scope: Mandate that every domain module exposes at least one SPI package, ships a DFX yaml covering five Design-for-X dimensions, and reserves the TCK module name for W2 conformance suites. Anchors CLAUDE.md Rule R-D sub-clause .a and ARCHITECTURE.md §4 #63.
- Cross-link: ADR-0064 (Layer-0 governing principles), ADR-0066 (Independent Module Evolution), ARCHITECTURE.md §4.7 (SPI purity).

## Context

The platform already has SPI packages under `agent-runtime` (`orchestration/spi/`, `memory/spi/`), but two architectural disciplines are missing:

1. **DFX clarity** — there's no per-module record of how each module addresses releasability, resilience, availability, vulnerability, and observability. Reviewers ask these questions on every release; the absence of a structured answer turns the audit into archaeology.
2. **TCK contract** — alternative SPI implementations (Postgres Checkpointer, Temporal RunRepository, etc.) will land in W2/W4. Without a TCK, "conformant implementation" has no enforceable definition.

Rule R-C.a requires the constraint and its enforcer ship in the same PR. The TCK conformance suites cannot be written today (no alternative implementations exist to test against), so the TCK module name + scaffolding requirement is deferred to W2. The SPI presence + DFX yaml requirements land active.

## Decision

### 1. SPI presence for `kind: domain` modules

Every module with `kind: domain` in its `module-metadata.yaml` MUST:

- Declare at least one entry under `spi_packages:` in the metadata file.
- Each declared package MUST exist as a directory under `<module>/src/main/java/`.
- Each SPI package MUST remain free of Spring, platform, inmemory-impl, Micrometer, and OpenTelemetry imports (extension of existing §4.7 — generalised by E48 ArchUnit `SpiPurityGeneralizedArchTest`).

Today: `agent-runtime` is the sole `kind: domain` module; its declared SPI packages are `com.huawei.ascend.service.runtime.orchestration.spi` and `com.huawei.ascend.service.runtime.memory.spi`.

### 2. DFX yaml for `kind: platform` and `kind: domain` modules

Required file: `docs/dfx/<module>.yaml` covering five dimensions, each with a non-empty body:

```yaml
module: <name>
kind: platform | domain
version: 1
generated_at: <ISO date>

releasability:   { ... }    # artifact coords, semver policy, deprecation window, packaging
resilience:      { ... }    # fallback paths, posture-aware defaults, cancellation contract
availability:    { ... }    # health endpoints, readiness signal, startup failure modes
vulnerability:   { ... }    # scan tooling, auth surface, tenant isolation, secret handling
observability:   { ... }    # metric namespace, trace carrier, log field shape
```

DFX is OPTIONAL for `kind ∈ {bom, starter, sample}`. The graphmemory starter ships a DFX yaml for parity, but the gate does not require it.

### 3. TCK module name reserved (deferred to W2)

The sibling `<module>-tck` reactor module and conformance suite are deferred per `CLAUDE-deferred.md` 32.b (TCK module skeleton) and 32.c (conformance content). Re-introduction trigger: first alternative implementation of any agent-runtime SPI is proposed (target: W2 Postgres Checkpointer or Temporal RunRepository).

Reserved name: `agent-runtime-tck` (sibling of `agent-runtime`). Adding it as a reactor module will require bumping `module_count_invariant` (Gate Rule 28e) from 4 to 5 at that time.

### 4. Enforcement

- Gate Rule R-E `dfx_yaml_present_and_wellformed` (enforcer E53) — every `kind: platform | domain` module has `docs/dfx/<module>.yaml` with all 5 DFX dimensions present.
- Gate Rule R-F `domain_module_has_spi_package` (enforcer E54) — every `kind: domain` module declares `spi_packages:` and each one resolves on disk.
- ArchUnit E48 `SpiPurityGeneralizedArchTest` — SPI purity (Spring/platform/inmemory/Micrometer/OTel-free).

### 5. Deferred sub-clauses

- `CLAUDE-deferred.md` 32.b — `<module>-tck` reactor module scaffolding. W2 trigger.
- `CLAUDE-deferred.md` 32.c — TCK conformance content (the actual conformance tests). W2 trigger.
- `CLAUDE-deferred.md` 32.d — Automated vulnerability scanner integration (SCA / CVE feed). W2 trigger.

## Alternatives considered

**Alt A — Ship TCK scaffolding today as a 5th reactor module.** Rejected: Gate Rule 28e (`module_count_invariant`) at ADR-write time asserted exactly 4 modules; adding a TCK module would force a rule change before the conformance content exists to justify it. (Post-write: the count was raised to 9 by the 2026-05-17 six-module materialization PR for a different reason; TCK module reservation remains W2 per CLAUDE-deferred.md 32.b.) Defer until alternative SPI implementations are proposed.

**Alt B — Treat DFX as a free-form section in `<module>/ARCHITECTURE.md`.** Rejected: ARCHITECTURE.md is prose; DFX is structured (5 fixed dimensions). A yaml lets the gate check schema completeness mechanically.

**Alt C — Apply DFX to all module kinds including BoM.** Rejected: BoM has no resilience surface, no health endpoints, and no SPI. Forcing 5-dimension DFX on it produces noise.

## Consequences

- **Positive**: Every domain and platform module declares its SPI surface and DFX posture in structured form; gate rules detect missing files mechanically; TCK name is reserved for W2.
- **Negative**: Two new docs to maintain (`docs/dfx/agent-service.yaml`, `docs/dfx/agent-service.yaml`); the starter DFX is voluntary but its presence creates a maintenance expectation; TCK content is deferred so SPI compatibility is asserted only by direct tests until W2.
- **Risk surfaced**: A `kind: domain` module added later without an `*.spi.*` package will fail the gate. Mitigation: the `module-metadata.yaml` `kind:` field is the contributor's commitment; choosing `kind: domain` means committing to SPI presence.

## Enforcers (Rule R-C.a)

- E48 ArchUnit `SpiPurityGeneralizedArchTest`.
- E53 Gate Rule R-E `dfx_yaml_present_and_wellformed`.
- E54 Gate Rule R-F `domain_module_has_spi_package`.

## §16 Review Checklist

- [x] SPI presence requirement is bound to `kind: domain` modules only.
- [x] DFX yaml is required for `kind ∈ {platform, domain}` only; optional for others.
- [x] Five DFX dimensions are enumerated.
- [x] TCK module name (`<module>-tck`) is reserved; conformance content is deferred with explicit W2 trigger.
- [x] Module count invariant interaction is acknowledged (E27 / Rule 28e — was hard-coded 4 at ADR-write; bumped to 9 by 2026-05-17 six-module materialization PR; will need further bumps when TCK lands and when Phase C collapses agent-platform + agent-runtime into agent-service).
- [x] §4 #63 anchors Rule R-D sub-clause .a in the architectural corpus.
