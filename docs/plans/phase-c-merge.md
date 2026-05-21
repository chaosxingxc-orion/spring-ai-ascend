---
level: L0
view: scenarios
authority: "ADR-0078 + ARCHITECTURE.md §2"
---

# Phase C — agent-platform + agent-runtime → agent-service

## §1 Status

- **Status:** DRAFT (transitions to ACCEPTED on first commit landing on `main`).
- **Branch:** `phase-c-merge` — six sequential commits, one PR.
- **Tracking ADR:** ADR-0078 — supersedes ADR-0055, extends ADR-0066, relates_to ADR-0026.
- **Transient counterpart:** `D:\.claude\plans\spicy-mixing-galaxy.md` (5-wave session plan; Phase C is its Wave 1).
- **Authority source:** `ARCHITECTURE.md` lines 75–86 — the corpus declares "Phase C follow-up will fold `agent-platform` + the runtime kernel into a single `agent-service`, returning the reactor to 8 substantive modules". This plan executes that intent.
- **Audit trail of decisions:** see `D:\.claude\plans\tokens-token-buzzing-sprout-agent-ac275631bde121e7d.md` §2 (Q1–Q5 with three options each) — user confirmed A/A/A/A/B on 2026-05-18.

---

## §2 Authoritative decisions

The five Phase C open questions were resolved by user input on 2026-05-18. Recommended answer for each question prevailed; rationale below cites the original Q&A in `tokens-token-buzzing-sprout-agent-ac275631bde121e7d.md` §2.

| Q | Decision | Option | Justification |
|---|---|---|---|
| **Q1 — Java package layout** | `com.huawei.ascend.service.platform.*` + `com.huawei.ascend.service.runtime.*` (two siblings under `service`) | **A** | Preserves the platform/runtime sub-layering invariant under one module root; smallest blast radius; Rule 21 generalises to "`service.runtime` MUST NOT import `service.platform`" without weakening defence-in-depth. |
| **Q2 — Flyway migrations** | Move `V1__init.sql` + `V2__idempotency_dedup.sql` as-is to `agent-service/src/main/resources/db/migration/`; update `gate/rls-baseline-grandfathered.txt` path. | **A** | Flyway tracks by migration name + checksum, not source path — zero production risk. Renumbering/squashing is out of scope for a topology move. |
| **Q3 — ADR strategy** | One new **ADR-0078** declares the merger; `supersedes: [ADR-0055]`, `extends: [ADR-0066]`, `relates_to: [ADR-0026]`. ADR-0055/0066 receive `superseded_by` / `extended_by` frontmatter back-references. | **A** | Honours Rule 25 (Architecture-Text Truth — accepted ADRs are immutable, so we add a new one rather than rewrite history) and Rule 34 (`supersedes`/`extends` sub-graphs must remain DAGs). |
| **Q4 — Wave alignment** | Land after PR-E2; PR-E2 shipped 2026-05-17 (commit `f8c8e95`). | **A** | No outstanding gate-script conflicts; clean base verified — gate run logs in `gate/log/runs/` confirm a clean PR-E2 baseline. |
| **Q5 — Single-PR vs phased** | Six sequential commits on `phase-c-merge` branch, one PR. | **B** | Each commit is a Rule 4 / Rule 9 ship gate (build-green precondition). PR ships them atomically; reviewers get bisect-friendly conceptual checkpoints rather than a single 213-file diff. |

---

## §3 File inventory

Verified counts as of 2026-05-18 baseline (prior to any move):

| Surface | Count | Notes |
|---|---|---|
| Java files **inside** `agent-platform/` + `agent-runtime/` | **164** | 41 main + 41 test (platform); 43 main + 39 test (runtime). |
| Java files **referencing** `com.huawei.ascend.platform.*` / `com.huawei.ascend.runtime.*` (import sites) | **167** | 164 in-module + 3 in `spring-ai-ascend-graphmemory-starter` (`GraphMemoryAutoConfiguration`, `GraphMemoryProperties`, one starter test). |
| Total corpus occurrences across `*.java`, `*.yaml`, `*.md`, `*.sh`, `*.sql` | **590** across **213 files** | Spans governance, ADRs, gate, reviews, releases. |
| `ARCHITECTURE.md` mentions | **42** | §2 module-layout table, §4 capability rows, prose paragraphs, code-fence paths. |
| Per-module `ARCHITECTURE.md` files folded | 2 | `agent-platform/ARCHITECTURE.md` + `agent-runtime/ARCHITECTURE.md` merge into one new file. |
| `module-metadata.yaml` files folded | 2 | Collapse into single `agent-service/module-metadata.yaml`. |
| `docs/dfx/*.yaml` files folded | 2 | `agent-platform.yaml` + `agent-runtime.yaml` → `agent-service.yaml`. |
| Flyway migrations (move-as-is) | 2 | `V1__init.sql`, `V2__idempotency_dedup.sql` (the latter is in `gate/rls-baseline-grandfathered.txt`). |
| `gate/check_architecture_sync.sh` line touches | **51 lines** | Rules 10, 21, 28j, 37, 38, 40, 65, 66 — every path that hard-codes `agent-platform/` or `agent-runtime/`. |
| `gate/test_architecture_sync_gate.sh` line touches | **12 lines** | Self-test paths + the new explicit-FAIL injection for retargeted Rule 21. |
| ADR `.md` / `.yaml` files mentioning these modules | **44 ADRs** | Includes ADR-0026, ADR-0055, ADR-0059, ADR-0066, ADR-0068. |
| Governance YAMLs to retarget | **4 files** | `architecture-graph.yaml` (237 of the 485 references), `architecture-status.yaml`, `enforcers.yaml`, `principle-coverage.yaml`. |

This is **module-topology consolidation**, not a rename — it touches every architectural surface in the corpus.

---

## §4 Package map

Source layout walked from `agent-platform/src/main/java/com/huawei/ascend/platform/` and `agent-runtime/src/main/java/com/huawei/ascend/runtime/`. Every sub-directory becomes one row; the test tree mirrors the main tree identically (each `src/main/java/...` row implies the parallel `src/test/java/...` move).

### `com.huawei.ascend.platform.*` → `com.huawei.ascend.service.platform.*`

| Old package | New package |
|---|---|
| `com.huawei.ascend.platform` | `com.huawei.ascend.service.platform` |
| `com.huawei.ascend.platform.auth` | `com.huawei.ascend.service.platform.auth` |
| `com.huawei.ascend.platform.engine` | `com.huawei.ascend.service.platform.engine` |
| `com.huawei.ascend.platform.idempotency` | `com.huawei.ascend.service.platform.idempotency` |
| `com.huawei.ascend.platform.observability` | `com.huawei.ascend.service.platform.observability` |
| `com.huawei.ascend.platform.persistence` | `com.huawei.ascend.service.platform.persistence` |
| `com.huawei.ascend.platform.posture` | `com.huawei.ascend.service.platform.posture` |
| `com.huawei.ascend.platform.probe` | `com.huawei.ascend.service.platform.probe` |
| `com.huawei.ascend.platform.resilience` | `com.huawei.ascend.service.platform.resilience` |
| `com.huawei.ascend.platform.tenant` | `com.huawei.ascend.service.platform.tenant` |
| `com.huawei.ascend.platform.web` | `com.huawei.ascend.service.platform.web` |
| `com.huawei.ascend.platform.web.runs` | `com.huawei.ascend.service.platform.web.runs` |

### `com.huawei.ascend.runtime.*` → `com.huawei.ascend.service.runtime.*`

| Old package | New package |
|---|---|
| `com.huawei.ascend.runtime` | `com.huawei.ascend.service.runtime` |
| `com.huawei.ascend.runtime.engine` | `com.huawei.ascend.service.runtime.engine` |
| `com.huawei.ascend.runtime.evolution` | `com.huawei.ascend.service.runtime.evolution` |
| `com.huawei.ascend.runtime.idempotency` | `com.huawei.ascend.service.runtime.idempotency` |
| `com.huawei.ascend.runtime.memory` | `com.huawei.ascend.service.runtime.memory` |
| `com.huawei.ascend.runtime.memory.spi` | `com.huawei.ascend.service.runtime.memory.spi` |
| `com.huawei.ascend.runtime.orchestration` | `com.huawei.ascend.service.runtime.orchestration` |
| `com.huawei.ascend.runtime.orchestration.inmemory` | `com.huawei.ascend.service.runtime.orchestration.inmemory` |
| `com.huawei.ascend.runtime.orchestration.spi` | `com.huawei.ascend.service.runtime.orchestration.spi` |
| `com.huawei.ascend.runtime.posture` | `com.huawei.ascend.service.runtime.posture` |
| `com.huawei.ascend.runtime.probe` | `com.huawei.ascend.service.runtime.probe` |
| `com.huawei.ascend.runtime.resilience` | `com.huawei.ascend.service.runtime.resilience` |
| `com.huawei.ascend.runtime.runs` | `com.huawei.ascend.service.runtime.runs` |
| `com.huawei.ascend.runtime.s2c` | `com.huawei.ascend.service.runtime.s2c` |
| `com.huawei.ascend.runtime.s2c.spi` | `com.huawei.ascend.service.runtime.s2c.spi` |

### `spring-ai-ascend-graphmemory-starter` (3 files)

Starter is a **sibling reactor module** — stays separate, but the 3 in-starter Java files + the Spring Boot `AutoConfiguration.imports` resource carry import strings that flip with the package rename above.

| File | Change |
|---|---|
| `spring-ai-ascend-graphmemory-starter/src/main/java/.../GraphMemoryAutoConfiguration.java` | package + imports rename |
| `spring-ai-ascend-graphmemory-starter/src/main/java/.../GraphMemoryProperties.java` | package + imports rename |
| `spring-ai-ascend-graphmemory-starter/src/test/java/.../GraphMemoryStarterTest.java` (or similar) | imports rename |
| `spring-ai-ascend-graphmemory-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `com.huawei.ascend.runtime.graphmemory.GraphMemoryAutoConfiguration` → `com.huawei.ascend.service.runtime.graphmemory.GraphMemoryAutoConfiguration` |

---

## §5 Gate-rule retargeting list

Every gate rule whose path patterns hard-code the old module roots. Source: `gate/check_architecture_sync.sh` (51 line touches) + `gate/test_architecture_sync_gate.sh` (12 line touches).

| Rule | Concern | OLD pattern | NEW pattern |
|---|---|---|---|
| **Rule 10** (`module_dep_direction`) | Cross-module forbidden imports | `agent-runtime → agent-platform` forbidden | Intra-module sub-package check: `com.huawei.ascend.service.runtime..` MUST NOT import `com.huawei.ascend.service.platform..` |
| **Rule 21** (Tenant Propagation Purity) | `service.runtime` MUST NOT import `service.platform` (e.g. `TenantContextHolder`) | `agent-runtime/src/main/java/com/huawei/ascend/runtime/**` ↛ `com.huawei.ascend.platform.**` | `agent-service/src/main/java/com/huawei/ascend/service/runtime/**` ↛ `com.huawei.ascend.service.platform.**` |
| **Rule 28j** (no zombie code in modules) | Path scan | `agent-platform/src/main/java/**`, `agent-runtime/src/main/java/**` | `agent-service/src/main/java/com/huawei/ascend/service/{platform,runtime}/**` |
| **Rule 37** (`no_blocking_io_in_runtime_main`) | No `RestTemplate` / `JdbcTemplate` in runtime production | `agent-runtime/src/main/java/**` | `agent-service/src/main/java/com/huawei/ascend/service/runtime/**` |
| **Rule 38** (`no_thread_sleep_in_business_code`) | No `Thread.sleep` in platform/runtime production | `agent-platform/src/main/java/**` + `agent-runtime/src/main/java/**` | `agent-service/src/main/java/com/huawei/ascend/service/{platform,runtime}/**` |
| **Rule 40** (RLS baseline) | Flyway migration scan + grandfather list | `agent-platform/src/main/resources/db/migration` | `agent-service/src/main/resources/db/migration` (and same path swap in `gate/rls-baseline-grandfathered.txt`) |
| **Rule 65** (`module_metadata_pom_dep_parity`) | Reconcile `module-metadata.yaml` vs `pom.xml` deps | Two rows: `agent-platform`, `agent-runtime` | One row: `agent-service` |
| **Rule 66** (`spi_package_exhaustiveness`) | `spi_packages:` matches on-disk SPI packages | Two manifests scanned | One manifest scanned; `spi_packages` = union of old two |

**Test-file retargeting (`gate/test_architecture_sync_gate.sh`, 12 lines):** the explicit-FAIL injection self-test for Rule 21 must inject a `service.runtime → service.platform` import and assert the gate catches it (replaces the old `runtime → platform` injection).

**Auxiliary grandfather list:** `gate/schema-first-grandfathered.txt` carries path-prefixed entries; scan for `agent-platform/` / `agent-runtime/` prefixes and update to `agent-service/...`. `sunset_date` values are NOT touched by Phase C.

---

## §6 Six-commit sequence

Each commit MUST build green; intermediate states are bisectable conceptual checkpoints. Detailed steps live in `D:\.claude\plans\spicy-mixing-galaxy.md` §"Wave 1 — Phase C".

### Commit 1 — Module skeleton + parent POM

Create empty `agent-service/` directory with `pom.xml`, `module-metadata.yaml`, `ARCHITECTURE.md`, `docs/dfx/agent-service.yaml`, empty `src/{main,test}/java/`. Update root `pom.xml` + `spring-ai-ascend-dependencies/pom.xml` BoM: drop the two old `<module>`s, add `<module>agent-service</module>`. Build-green gate: `mvn -pl agent-service -am compile` succeeds (empty module compiles).

### Commit 2 — Move sources verbatim (no package rename)

`git mv` Java sources + tests + resources (`db/migration/*.sql`, `META-INF/`, `application*.properties`) from `agent-platform/` and `agent-runtime/` into `agent-service/` keeping the old `com.huawei.ascend.platform/` and `com.huawei.ascend.runtime/` package directories. Delete now-empty `agent-platform/` and `agent-runtime/`. Update `gate/rls-baseline-grandfathered.txt` paths. Build-green gate: `mvn -pl agent-service verify` passes — code unchanged, just relocated.

### Commit 3 — Package rename to `service.{platform,runtime}`

Mechanical bulk swap: 164 in-module `package com.huawei.ascend.platform.*` → `package com.huawei.ascend.service.platform.*` (same for runtime); 167 import-site files (164 in-module + 3 in graphmemory-starter); the starter's `AutoConfiguration.imports` resource line. Move source directories. Retarget ArchUnit tests in place: `RuntimeMustNotDependOnPlatformTest` → `ServiceRuntimeMustNotDependOnServicePlatformTest`; `TenantPropagationPurityTest` (Rule 21) → patterns updated; `PlatformImportsOnlyRuntimePublicApiTest` → equivalent. Build-green gate: `mvn verify` passes.

### Commit 4 — Governance + gate-script retargeting

Bulk replace in the 4 governance YAMLs (`architecture-graph.yaml`, `architecture-status.yaml`, `enforcers.yaml`, `principle-coverage.yaml`): `agent-platform`/`agent-runtime` → `agent-service`; `com.huawei.ascend.platform.*`/`com.huawei.ascend.runtime.*` → `com.huawei.ascend.service.*`; then de-duplicate merged rows. Apply the §5 retargeting table to `gate/check_architecture_sync.sh` (51 lines) + `gate/test_architecture_sync_gate.sh` (12 lines). Update `gate/schema-first-grandfathered.txt`. Bump `architecture-status.yaml#repository_counts.reactor_modules` 9 → 8 and `internal_modules` 7 → 6 (Phase C alone; T2.B2 adds the runtime-core module back in Wave 2). Build-green gate: `bash gate/check_architecture_sync.sh` passes all rules.

### Commit 5 — Documentation corpus retarget

Update `ARCHITECTURE.md` §2 (42 mentions; drop two rows, add one; reactor count 9 → 8; §-marker citing ADR-0078), `README.md` module-list, `CLAUDE.md` Rules 21/29/37/38/39/40/41 kernel paragraphs (regenerated via `gate/build_claude_md_from_cards.sh` after card updates). Bulk-replace module paths in 44 ADR files (`.md` + `.yaml`) leaving historical decisions intact; ADR-0055/0066 get `superseded_by`/`extended_by` frontmatter pointing at ADR-0078. Update 3 release notes (only the unreleased one may be edited; closed releases get an addendum). Append `docs/governance/rule-history.md` entry. Populate `agent-service/ARCHITECTURE.md` by merging the two old per-module ARCHITECTURE files (L1, view: scenarios).

### Commit 6 — Verification + graph regen

Run `mvn verify` (full reactor); `bash gate/test_architecture_sync_gate.sh` (self-tests green; count bumped by the new Rule 21 injection test); `bash gate/check_architecture_sync.sh` (all rules pass); `python gate/build_architecture_graph.py` regenerates `architecture-graph.yaml` idempotently (Rule 34). Commit final `architecture-status.yaml` count values.

---

## §7 Verification surface

Run from project root at the end of Wave 1 (Phase C only — full 5-wave verification lives in the session plan §"Verification (end-of-wave checklist)").

```bash
# 0. Linux-first per Rule 74 — Git Bash is debugging only.
uname -a   # expect: Linux (WSL2 or native), NOT MINGW/MSYS

# 1. Module count: 8 substantive modules (BoM + 5 agent-* + agent-service + graphmemory-starter)
grep -E "^\s+<module>" pom.xml | wc -l
# expect: 8

grep "reactor_modules:" docs/governance/architecture-status.yaml
# expect: 8

# 2. Old modules deleted
test ! -d agent-platform && test ! -d agent-runtime && echo "Phase C structural OK"

# 3. New package layout present on disk
test -d agent-service/src/main/java/com/huawei/ascend/service/platform \
  && test -d agent-service/src/main/java/com/huawei/ascend/service/runtime \
  && echo "Phase C package layout OK"

# 4. No corpus references to the dead package roots
grep -rn "ascend\.springai\.platform\." --include="*.java" .  # expect: 0 matches
grep -rn "ascend\.springai\.runtime\."  --include="*.java" .  # expect: 0 matches

# 5. Full Maven verify (reactor build + ArchUnit + ITs)
./mvnw verify
# expect: BUILD SUCCESS

# 6. Full gate green
bash gate/check_architecture_sync.sh
# expect: GATE: PASS

# 7. Self-tests green (including the new Rule 21 retarget injection test)
bash gate/test_architecture_sync_gate.sh
# expect: all tests pass

# 8. Architecture graph regenerates idempotently (Rule 34)
python gate/build_architecture_graph.py
git diff --exit-code docs/governance/architecture-graph.yaml
# expect: no diff
```

If all 8 steps pass, Phase C is shippable per Rule 9.

---

## §8 Risk register

Top 5 risks (excerpted from session plan; mitigations are commit-aligned and reviewer-actionable).

| # | Risk | Mitigation |
|---|---|---|
| 1 | The 213-file PR is too large to review safely. | Six-commit shape gives bisectable conceptual checkpoints; each commit MUST build green, so reviewers can `git checkout` any commit and run `mvn verify`. |
| 2 | Phase C silently weakens Rule 21 (TenantPropagationPurityTest) — wrong package pattern in the retarget could neutralise the invariant. | Commit 4 includes an EXPLICIT FAIL self-test in `gate/test_architecture_sync_gate.sh`: injects a `service.runtime → service.platform` import on a throw-away file and asserts the gate catches it. Drafted in pre-flight by Agent W1-C. |
| 3 | Bulk rename breaks Spring Boot AutoConfiguration discovery — `spring-ai-ascend-graphmemory-starter`'s `AutoConfiguration.imports` resource carries a fully-qualified class name that flips with the package rename. | Commit 3 explicitly updates `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Smoke test in Commit 6: `mvn -pl spring-ai-ascend-graphmemory-starter verify` exercises the autoconfig path. |
| 4 | ADR-0078 cites superseded ADRs that future review reads as "broken history" — Rule 34 requires `supersedes`/`extends` sub-graphs to be DAGs. | ADR-0078 explicitly lists `supersedes: [ADR-0055]`, `extends: [ADR-0066]`, `relates_to: [ADR-0026]`. ADR-0055 + ADR-0066 receive matching `superseded_by` / `extended_by` frontmatter back-references. Wave 5 `python gate/build_architecture_graph.py` re-derives the DAG and fails if any cycle appears. |
| 5 | `architecture-status.yaml` count drift across commits (reactor_modules + internal_modules + enforcer count). | Commit 4 lands `reactor_modules: 9 → 8` and `internal_modules: 7 → 6` in the same commit as the structural change. Commit 6 re-derives counts from authoritative inputs and asserts `architecture-status.yaml` matches. Rule 64 (`module_count_data_driven`) catches any residual drift. |

Additional non-top-5 risks (carrying over from session plan): in-flight stash conflicts (pre-flight first action is `git stash list` snapshot + work on a fresh branch); Flyway path-as-key false alarm (resolved — Flyway tracks by name + checksum); Rule 74 Git Bash-only verification (every wave's `mvn verify` MUST run under WSL2 or native Linux before commit).

---

## Cross-references

- Session plan (transient, 5 waves): `D:\.claude\plans\spicy-mixing-galaxy.md`
- Original Phase C draft (most detailed source, Q1–Q5 audit trail): `D:\.claude\plans\tokens-token-buzzing-sprout-agent-ac275631bde121e7d.md`
- Authority statement: `D:\chao_workspace\spring-ai-ascend\ARCHITECTURE.md` lines 75–86
- Tracking ADR (to be authored in pre-flight): `docs/adr/0078-agent-service-consolidation.yaml`
- Superseded: `docs/adr/0055-*` (cross-module dep direction)
- Extended: `docs/adr/0066-*` (independent module evolution — 8 modules instead of 9)
- Related: `docs/adr/0026-*` (already superseded by ADR-0055)
