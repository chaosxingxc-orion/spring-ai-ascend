---
level: L0
view: scenarios
status: active
authority: "ADR-0147 Wave 6 sunset roadmap"
---

# Wave 6 — YAML Authority Sunset Roadmap

**Authority:** ADR-0147 (Structurizr Workspace Authority).

Wave 5 (commit `1026bbc`, 2026-05-27) flipped the architecture-workspace gate
to blocking and amended Rule G-1.b to name `architecture/workspace.dsl` as
the machine-readable architecture authority. The 14-day soak runs until
**2026-06-10**. Wave 6 starts AFTER soak passes clean.

Wave 6's purpose: flip the authority direction of three YAML surfaces that
the workspace closure now mirrors. They become **generated projections of
the workspace**, not source-of-truth. Engineers edit the workspace; the
yaml is regenerated.

## Sunset targets (W6 sub-waves)

Each sub-wave is its own commit + its own soak window (15 days each;
parallel allowed once sub-wave .a is green for 30 days).

### Sub-wave 6.a — `docs/governance/enforcers.yaml`

**Direction flip:** today, engineers edit `enforcers.yaml`; W3
`EnforcersFragmentEmitter` reads it and emits `architecture/generated/enforcers.dsl`.
After 6.a: engineers edit `architecture/features/enforcers.dsl` (NEW, authored);
`WorkspaceToEnforcersYamlEmitter` (NEW; not yet built) emits `enforcers.yaml`
byte-identical to the workspace's view; the existing yaml gets a
"DO NOT HAND EDIT" header.

**Deliverables:**
1. `tools/architecture-workspace/.../fragment/WorkspaceToEnforcersYamlEmitter.java`
   — reverse direction emitter.
2. Round-trip test: emit yaml from workspace, diff against the
   current `enforcers.yaml`; must be empty.
3. Gate addition: re-emit yaml on every workspace change; fail if
   non-empty diff against committed yaml.
4. `enforcers.yaml` gets a `# DO NOT HAND EDIT — generated from
   architecture/workspace.dsl per ADR-0147` header.

**Open question:** the W3 `EnforcersFragmentEmitter` reads yaml; the
W6 reverse emitter writes yaml. They mirror each other and could
share a Java record type. Settle this during sub-wave design.

### Sub-wave 6.b — `docs/governance/principle-coverage.yaml`

Same pattern as 6.a. The principle-coverage authority moves to
`architecture/features/principles.dsl` (NEW); a reverse emitter
regenerates the yaml.

### Sub-wave 6.c — `docs/governance/architecture-status.yaml#capabilities`

ONLY the `capabilities:` block moves to workspace authority. The
`baseline_metrics`, `strategic_decisions`, and other top-level keys
remain authored in the yaml (they have other consumers).

Implementation requires a partial-yaml rewriter that preserves
non-`capabilities` content. This is the most invasive of the three
sub-waves.

### Sub-wave 6.d — Generated-projection headers

For each sunset yaml, the surface-classification.yaml registry
adds an entry classifying it as a `templated`/`hybrid` bucket. The
Rule G-13 byte-identity check then runs on the yaml.

## What stays as YAML (NOT sunset)

| File | Reason |
|---|---|
| `*/module-metadata.yaml` | Consumed directly by Maven build + ArchUnit tests. Workspace mirror at `architecture/generated/modules.dsl` is the authority view; the yaml stays as the operational format. |
| `docs/adr/*.yaml` | ADRs remain hand-authored Markdown/YAML. The workspace `architecture/decisions/` directory mirrors them at build time (W5+). |
| `docs/governance/recurring-defect-families.yaml` | Out of scope for the architecture-authority migration; G-9.b freshness gate remains. |
| `docs/governance/architecture-status.yaml#baseline_metrics` | Numeric baseline ledger has its own gate (Rule G-8.a + Rule 28). |
| `docs/governance/templates/surface-classification.yaml` | Rule G-13 registry; the workspace's `architecture/generated/surface-classification.dsl` is a downstream projection. |

## Schedule

| Wave | Earliest start | Predecessor |
|---|---|---|
| W5 soak | 2026-05-27 | W5 commit landed |
| W5 soak ends | 2026-06-10 | 14 days |
| **W6.a (enforcers)** | 2026-06-10 | W5 soak clean |
| W6.a soak ends | 2026-06-25 | 15 days |
| W6.b (principles) | 2026-06-25 | W6.a clean |
| W6.b soak ends | 2026-07-10 | 15 days |
| W6.c (capabilities) | 2026-07-10 | W6.b clean |
| W6.c soak ends | 2026-07-25 | 15 days |
| W7 cleanup | 2026-07-25 | W6.c clean |

Total wall-clock from W5 commit to W7 start: **≈8.5 weeks**.

## Rollback

W6 sub-waves are rollback-cheap if NO yaml deletion has occurred —
just revert the `# DO NOT HAND EDIT` header commits. After W7's
final yaml retirement, restoring the legacy source-of-truth direction
requires recreating the build_architecture_graph.py inputs (expensive).

## What this commit (W6 documentation, 2026-05-27) ships

This commit lands the roadmap doc only. The reverse emitters
(`WorkspaceTo*YamlEmitter`) land in subsequent W6 sub-wave commits
after the soak gate clears. The intent is to make the W6 work plan
explicit and reviewable WITHOUT committing to the soak-blocked
implementation early.
