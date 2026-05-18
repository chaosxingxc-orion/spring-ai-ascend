---
release_tag: v2.0.0-rc9
release_date: 2026-05-19
release_type: corrective_uplift
supersedes_tag: v2.0.0-rc8
retracts_tag: null
authority: docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md
response_doc: docs/reviews/2026-05-19-l0-rc8-post-corrective-architecture-review-response.en.md
adr: docs/adr/0083-rc9-corpus-truth-and-ci-acceptance.yaml
---

# v2.0.0-rc9 — rc8 post-corrective review response + CI-green restoration (2026-05-19)

## One-liner

> v2.0.0-rc9 closes all 7 findings of the rc8 post-corrective architecture review (Codex), introduces 6 new prevention gate rules (91–96), reconciles the executable-vs-ledger gate count taxonomy, retires two orphan authority surfaces (`docs/STATE.md`, `docs/dfx/agent-platform.yaml`), and restores CI green on `main` for the first time since rc1 by closing six latent production defects masked by local Docker-less verification.

## Baseline counts (post-rc9)

| metric | count | delta vs rc8 |
|---|---|---|
| §4 constraints | 65 | unchanged |
| Active ADRs | 83 | +1 (ADR-0083) |
| Layer-0 governing principles | 13 | unchanged |
| Active engineering rules | 51 | +6 (Rules 91–96) |
| Active gate rules | 108 | +34 (rc8 baseline 74 was "rule families"; rc9 reconciles to executable-section count per Rule 91, closing the 28-section gap, + adds 6 new rules) |
| Gate self-test cases | 161 | +12 (2 per Rule 91–96) |
| Enforcer rows | 116 | +12 (E123–E134) |
| Maven tests GREEN (under `./mvnw verify`) | 371 | unchanged (rc9 production changes are annotation/property-only) |
| Architecture graph | 360 nodes / 510 edges | +12 nodes / +24 edges (rule cards + enforcer pairs + ADR-0083) |
| CI runs on `main` GREEN (since repo creation) | 1 | First green run; previously 48 / 50 = 96% red |

## Findings closed

All 7 rc8 post-corrective review findings accepted; none rejected. See `docs/reviews/2026-05-19-l0-rc8-post-corrective-architecture-review-response.en.md` for per-finding detail and ADR-0083 for the authoritative decision record.

### Family taxonomy (rc9 wave)

- **H-α** (P0-1, P1-4): Manifest-truth / count-derivation drift. Reconciled `active_gate_checks` to the executable-section count + narrowed Rule 89 scope across `enforcers.yaml` + `gate/README.md`. Closed by **Rule 91 (`baseline_metric_matches_executable_manifest`)**.
- **H-β** (P0-2, P0-3, P1-3): Orphan / deleted-name authority surfaces. Archived `docs/STATE.md` + deleted orphan `docs/dfx/agent-platform.yaml` + updated deleted-module name references across `ARCHITECTURE.md` #59, `McpReplaySurfaceArchTest` Javadoc, `rule-37.md`, `agent-client/ARCHITECTURE.md`, `agent-evolve/ARCHITECTURE.md`, `agent-runtime-core/ARCHITECTURE.md`. Closed by **Rules 92, 93, 94**.
- **H-γ** (P1-2): SPI catalog completeness. Added `SkillCapacityRegistry` as the 12th active SPI row in `docs/contracts/contract-catalog.md`. Closed by **Rule 95 (`spi_catalog_exhaustiveness`)**.
- **H-δ** (P1-1): Active/deferred kernel-truth boundary. Narrowed Rule 42 + Rule 46 kernels to shipped scope; corrected Rule 46 sub-clause naming (46.b = capacity wiring, 46.c = non-blocking lifecycle; invalid-response is shipped, not deferred). Closed by **Rule 96 (`kernel_deferred_clause_coherence`)**.
- **H-ε** (P2-1): Shadow-corpus freshness. Regenerated `gate/rules/` via `gate/lib/extract_rules.sh`; rewrote `gate/lib/orchestrator.sh` comments to mark the directory as IDE-only. Closed by **Rule 92** (shared with H-β).

## CI defects bundled in the same wave

| Defect | Surface | Fix |
|---|---|---|
| `NoOpAsyncRunDispatcher` `@ConditionalOnMissingBean` on `@Component` | Blocked ~30 Testcontainers ITs on Linux CI | Dropped annotation; tests override via `@Primary` |
| `IdempotencyStoreAutoConfiguration` `@ConditionalOnBean(DataSource.class)` Boot 4 ordering | `No qualifying bean of type IdempotencyStore` under `web-application-type=none` ITs | Dropped condition + Javadoc explaining ordering hazard |
| `WebSecurityConfig` `HttpSecurity` autowire under non-web tests | `IdempotencyDurabilityIT`, `IdempotencyStorePostgresIT` | Added `@ConditionalOnWebApplication(type = SERVLET)` |
| `PostureBindingIT` missing JdbcIdempotencyStore under research posture | `PostureBootGuard` rejected startup | `@TestConfiguration RunRepositoryFixture` now provides `@Primary IdempotencyStore` from Testcontainers DataSource |
| Spring AI 2.0.0-M5 eager credential validation | `OpenAiAudioSpeechAutoConfiguration` failed under no-key CI | Declared `spring.ai.openai.api-key=${OPENAI_API_KEY:dummy-no-call-expected}` + Anthropic counterpart |
| `RunResponse` springdoc schema drift | `OpenApiContractIT.liveSpecResponseSchemasMatchPinnedRequiredFields` failed | Added `@Schema(requiredMode = REQUIRED)` to each record component |

## New prevention gate rules (Rules 91–96)

| # | Slug | Enforcers | Closes |
|---|---|---|---|
| 91 | `baseline_metric_matches_executable_manifest` | E123, E124 | P0-1 |
| 92 | `gate_rules_corpus_freshness` | E125, E126 | P2-1 |
| 93 | `dfx_stem_matches_module` | E127, E128 | P0-3 |
| 94 | `active_corpus_deleted_module_name_truth` | E129, E130 | P1-3 |
| 95 | `spi_catalog_exhaustiveness` | E131, E132 | P1-2 |
| 96 | `kernel_deferred_clause_coherence` | E133, E134 | P1-1 |

## Files moved / deleted

- `docs/STATE.md` → `docs/archive/2026-05-19-STATE-md-archived/STATE.md` (with non-authoritative front-matter banner).
- `docs/dfx/agent-platform.yaml` → DELETED (orphan after ADR-0078; closed by Rule 93).

## Four pillars

- **performance**: unchanged at rc9 (rc8 baseline holds; CI restoration unblocks the first end-to-end measurement under Linux + Docker since rc1).
- **cost**: unchanged at rc9.
- **developer_onboarding**: improved — `ARCHITECTURE.md:861` no longer leads readers to a stale ledger; `gate/rules/` carries explicit IDE-only-artifact comments; rule-card pointers to deferred sub-clauses make the active/deferred boundary navigable from either side.
- **governance**: improved — manifest-vs-ledger axis now enforced (Rule 91); orphan authority detection institutionalised (Rule 93); deleted-module name truth widened beyond a single yaml field (Rule 94); SPI catalog completeness bidirectional (Rule 95); kernel/deferred coherence bidirectional (Rule 96); gate/rules freshness mechanised (Rule 92).

## Verification

```bash
bash gate/check_parallel.sh             # GATE: PASS, parallel_summary: executed 108 rules
bash gate/test_architecture_sync_gate.sh # Tests passed: 161/161
python gate/build_architecture_graph.py # 360 nodes / 510 edges; idempotent
./mvnw -B -ntp verify                    # 371 tests GREEN (CI Linux + Docker)
gh.exe run view <rc9-run-id>             # conclusion: success
```

## Authority

- ADR-0083 — accepts all 7 findings of `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` (Codex, 2026-05-18).
- Response document: `docs/reviews/2026-05-19-l0-rc8-post-corrective-architecture-review-response.en.md`.
- Tag rc9 supersedes rc8; rc8 NOT retracted (carries `superseded_by_tag: v2.0.0-rc9` + inline historical marker per Rule 28 baseline-truth exemption).
