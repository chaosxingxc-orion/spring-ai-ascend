# spring-ai-ascend skill bundle — downstream adoption guide

This directory ships the 6-phase scenario-loaded contract pattern from
ADR-0098 (rc21) as a deliverable for downstream platform consumers —
teams building agent runtimes on top of spring-ai-ascend.

The in-repo `.claude/skills/<phase>-mode.md` files are the **instance**
used by spring-ai-ascend itself; the files under
[`templates/`](templates/) and [`contract-templates/`](contract-templates/)
are the **portable form** with `{{PLACEHOLDER}}` markers that downstream
teams fill in with their own rule sets / module names / contract paths.

## Why adopt this pattern?

Progressive on-demand rule loading in CLAUDE.md-style governance leaves
many rules "ghosting" during work-time. Phase-scoped contract loading
via skills surfaces only the rules active for the current work phase,
which the model attends to more reliably. See `docs/logs/releases/2026-05-21-l0-rc21-scenario-phase-contracts.en.md`
for the empirical motivation.

## 6-step adoption flow

1. **Establish a `.claude/skills/` directory in your repo** (if not
   already present). Claude Code auto-loads skills from this path on
   session start.

2. **Copy the skill templates you need from
   [`templates/`](templates/) into your `.claude/skills/`**. The 5
   phase skills are `{design,impl,verify,commit,review}-mode.template.md`.
   You may omit any phase you don't model (e.g. teams that don't run
   formal reviewer cycles can skip `review-mode`).

3. **Fill in the `{{PLACEHOLDER}}` markers** in each template:
   - `{{PROJECT_NAME}}` — your project's name as it appears in
     prose.
   - `{{CONTRACTS_DIR}}` — directory holding your phase contracts.
   - `{{RULE_IDS_BY_PHASE}}` — list of your rule ids per phase.
   - `{{REFRESH_TRIGGER_SIGNALS}}` — your governance refresh signals
     for the `/refresh-defect-archive` analogue (if used).

4. **Author your phase contracts** in
   [`contract-templates/`](contract-templates/) using the
   structural skeleton. Each contract declares: `level`, `view`,
   `scope_phase`, `status`, an Active Rules table (with **P** /
   **X** markers), Forbidden Patterns, Exit Criteria, and Composes
   With pointers to adjacent phases.

5. **Add a Phase Entry directive table to your project's CLAUDE.md
   (or equivalent always-loaded context)**. Point at your 5 (or
   fewer) skills with MUST-invoke language. See spring-ai-ascend's
   [`CLAUDE.md`](../../CLAUDE.md) §Phase Entry for the canonical
   shape.

6. **Verify each skill loads in a fresh session.** Open a new Claude
   Code session, invoke `/<phase>-mode`, confirm the matching
   contract is surfaced into context. If the contract is large, the
   skill description should still trigger the right load via Claude
   Code's skill index.

## Customisation guide

### Which phases can be omitted?

- **Always-on** — never omit. It's CLAUDE.md itself.
- **Architecture design** — omit if your team doesn't author
  formal ADRs. Most platform teams keep this.
- **Engineering implementation** — never omit. Widest coverage; the
  default fallback skill when phase is ambiguous.
- **Integration verification** — omit if you don't run gates or
  integration tests locally.
- **System commit** — omit if your release process is fully
  automated and doesn't need pre-commit discipline.
- **Review response** — omit if your team doesn't model reviewer
  cycles.

### Rule namespace convention

spring-ai-ascend uses `D-/R-/G-/M-` prefixes. Downstream teams use
their own conventions. The templates accept any rule-id prefix —
just maintain consistency within your project.

### Always-on rule shrinkage

spring-ai-ascend's CLAUDE.md keeps every rule kernel inline (no shrink
in rc21 — see ADR-0098 §deferred_work). If your project's CLAUDE.md
has room to shrink, you may move phase-scoped rule kernels INTO the
phase contracts and update your equivalent of Rule 68 (kernel-card
byte-match) to accept contract-hosted kernels. Note this is non-trivial
and we recommend starting with the additive form (kernels stay in
CLAUDE.md, contracts add the routing).

## Upstream sync flow

When spring-ai-ascend lands a new rcNN with updated contracts or new
phase skills, downstream consumers can:

1. Diff `dist/skills/` between their current consumed version and
   the new one.
2. Cherry-pick changes that apply (most often: new forbidden patterns
   in a phase contract; new exit criteria; updates to dual-track
   loading docs).
3. Re-customise any `{{PLACEHOLDER}}` markers that changed.
4. Update their own ADR / changelog with the sync commit.

The `manifest.yaml#manifest_version` field changes with each rc; you
can pin a specific version or track HEAD per your preference.

## License + attribution

This bundle inherits the spring-ai-ascend repository's `LICENSE`. If
you fork the templates, you may retain or remove the attribution
comments at your discretion — the pattern itself is not encumbered.

## Not in scope here

- A marketplace plugin (e.g. `ce-skills-spring-ai-ascend`) — explicit
  out-of-scope per ADR-0098 §deferred_work; may land in rc22+.
- Full per-skill placeholder fleshing-out — rc21 ships
  `manifest.yaml` + this `README.md` as minimum-viable. Per-template
  customisation work lands later.
- Pinning of third-party plugins (everything-claude-code, claude-mem,
  planning-with-files, etc.) — out-of-scope per the rc21 plan; each
  developer manages their own plugin install.

## See also

- [`../../docs/adr/0098-rc21-scenario-phase-contracts-and-new-discipline-rules.yaml`](../../docs/adr/0098-rc21-scenario-phase-contracts-and-new-discipline-rules.yaml)
- [`../../docs/governance/contracts/`](../../docs/governance/contracts/) — the in-repo instance contracts
- [`../../.claude/skills/`](../../.claude/skills/) — the in-repo instance skills
- [`../../CLAUDE.md`](../../CLAUDE.md) §Phase Entry — the canonical dual-track directive
