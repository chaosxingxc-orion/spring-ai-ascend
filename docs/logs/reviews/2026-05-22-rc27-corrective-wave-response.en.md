---
level: L0
view: process
affects_level: L0, L1
affects_view: development, scenarios, logical
proposal_status: response
date: 2026-05-22
authors: ["chao", "急急 (agent)"]
responds_to:
  - "(internal) 2026-05-22 codebase review (adversarial + correctness + project-standards reviewers)"
# Rule 44 frozen-doc gate uses single-line regex `affects_artefact:.*<file>`
# so we encode the per-file claims inline below (the canonical multi-line list
# also appears at end of file for human reading).
affects_artefact:
  - ARCHITECTURE.md
  - CLAUDE.md
  - docs/adr/0104-rc22-package-root-migration-to-com-huawei-ascend.yaml
  - docs/governance/rules/rule-G-1.1.md
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/recurring-defect-families.md
  - docs/governance/architecture-status.yaml
  - docs/governance/enforcers.yaml
  - docs/contracts/contract-catalog.md
  - docs/contracts/engine-hooks.v1.yaml
  - agent-bus/ARCHITECTURE.md
  - agent-client/ARCHITECTURE.md
  - agent-evolve/ARCHITECTURE.md
  - agent-execution-engine/ARCHITECTURE.md
  - agent-middleware/ARCHITECTURE.md
  - agent-service/ARCHITECTURE.md
  - gate/check_parallel.sh
  - gate/check_architecture_sync.sh
  - gate/lib/check_l1_dev_view_tree.sh
  - gate/lib/check_l1_spi_appendix.sh
  - gate/lib/scan_cache.sh
  - gate/lib/fast_grep.sh
  - gate/always-loaded-budget.txt
  - gate/config.yaml
  - gate/config.schema.yaml
related_adrs:
  - ADR-0099
  - ADR-0100
  - ADR-0101
  - ADR-0102
  - ADR-0103
  - ADR-0104
---

# rc27 Corrective Wave — Response to 2026-05-22 Codebase Review

## Verdict

The codebase review of the 6-wave stack (rc22..rc26, PRs #36/#42/#43/#44/#45/#46) surfaced **7 critical + 6 high + 7 medium/low defects**. Most critical: the rc22 per-rule timeout implementation silently dropped all `fail_rule`/`pass_rule` calls because the `bash -c` subshell did not inherit shell functions. The gate was effectively a no-op across rc22-rc26. CI green meant nothing.

rc27 ships corrective fixes for all 13 critical+high defects in a single wave.

## Family taxonomy (wave letter: O — rc27)

| Family | Cited findings | Defect class | Status |
|---|---|---|---|
| **O-α** | ADV-1, CORR-1 | Gate execution model: timeout subshell drops shell functions | FIXED — `export -f fail_rule pass_rule`; timeout child re-sources prologue; `--preserve-status` dropped |
| **O-β** | ADV-2 | rc22.5 rename incomplete (slash-form paths) | FIXED — bulk sed across all `gate/**/*.sh` + `docs/**/*.yaml`/`.md` (32 files, 187 replacements) |
| **O-γ** | rc22-1 | ADR-0104 self-corrupted by its own rename script | FIXED — full ADR rewrite with bracketed-letter encoding of from-side namespace + PR #42 commit pointer |
| **O-δ** | rc22-2 | Rule G-1.1 placeholder pass_rule (helpers + fixtures absent) | FIXED — real `gate/lib/check_l1_dev_view_tree.sh` + `check_l1_spi_appendix.sh` + 6 fixtures shipped |
| **O-ε** | rc22-3 | rc26 SPIs under non-.spi packages (Rule R-D.d violation) | FIXED — git mv to `.spi.` packages + module-metadata/DFX/catalog updates |
| **O-ζ** | rc22-4 | Rule M-1 violation (agent-evolve skeleton but ships production code) | FIXED — frontmatter flipped `skeleton → active` + prose updated |
| **O-η** | rc22-5, rc22-6, rc22-7, rc22-8 | Contract catalog drift (3 missing design_only rows + 3 missing rc23 SPI rows + IdempotencyStore wrong SPI claim + agent-evolve wrong cross-module FQNs) | FIXED — catalog table rewritten with 17 SPI rows + 9 design_only contracts |
| **O-θ** | ADV-3 | InMemoryTaskStateStore cross-tenant save | FIXED — IllegalStateException on cross-tenant overwrite |
| **O-ι** | ADV-5 | AgentInvokeRequest null-field tolerance | FIXED — canonical constructor with Objects.requireNonNull |
| **O-κ** | Rule D-9 | Java files contain rc/ADR/Wave metadata in Javadocs | FIXED — bulk per-line scrub across 34 Java files (indent-preserving regex) |

## Rejections sent to reviewer

None. All findings accepted.

## Verification performed

- WSL Ubuntu: `bash gate/check_parallel.sh` (post-rc27 fixes) — gate now WORKS (catches real violations rc22-rc26 silently hid)
- Smoke-tested awk helpers (`awk_multi_match`, `awk_count_matches`, `awk_files_with_match`) on `_SCAN_AGENT_JAVA_MAIN`
- Verified per-rule timeout child re-sources prologue + has `fail_rule`/`pass_rule` defined (export -f as defence-in-depth)
- Verified `--preserve-status` dropped so timeout exits 124 (not 143/137)
- Cross-module SPI grounding parity: agent-client appendix references agent-bus's IngressGateway as a CONSUMED SPI (correctly tolerated by rc27 helper's cross-module rule)

## Lessons captured

- A "green CI" badge depends on the gate ACTUALLY running each rule. If `bash -c` strips shell functions, every fail_rule becomes a no-op and the gate green-washes.
- Bulk-rewrite scripts (rc22.5) can self-corrupt their own ADR. Encode immutable from-side literals to defeat re-substitution.
- Placeholder `pass_rule` stubs that ship instead of real implementations create dual-truth: baseline advertises enforcement, gate output reports PASS, but the rule never runs. Avoid pass_rule stubs in production gate code; use explicit `# TODO: rule body lands rcN` comment with a separate corrective ADR.
- Indent-preserving regex requires per-line scanning + careful multi-space collapse. The first scrub attempt (rc27 attempt-1) used `re.sub(r'  +', ' ', text)` which destroyed Java indentation; restored via `git checkout HEAD` + redid with per-line patterns that don't touch leading whitespace.
- Rule D-9's "per ADR-NNNN" detection is single-line — multi-line Javadoc continuations naturally bypass it, but should still be scrubbed for documentation hygiene.

## Out of scope (deferred to next wave)

- ADV-4: A2A enum case-mismatch (lowercase yaml vs UPPER Java) — deferred per ADR-0100 §non_goals contract-only adoption; runtime wiring lands with W2 ModelGateway.
- CORR-2 / CORR-3 / CORR-4 / CORR-5 / CORR-6 / CORR-7: medium-severity correctness issues in fast_grep + awk helpers (mktemp leak, BSD xargs incompatibility, error masking, Rule 28k duplicate find path, TSV empty-art edge case, total-gate timeout warning surface). Acceptable defects; will land in a future cleanup wave.
