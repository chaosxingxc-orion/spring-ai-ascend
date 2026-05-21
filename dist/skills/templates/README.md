# Skill templates (placeholder)

This directory will ship the 6 per-skill `*.template.md` files that
downstream platform consumers copy into their `.claude/skills/` and
customise.

Per [ADR-0098](../../../docs/adr/0098-rc21-scenario-phase-contracts-and-new-discipline-rules.yaml)
§deferred_work, the per-skill template fleshing-out is deferred to
follow-up. rc21 ships the manifest + adoption guide (`../manifest.yaml`
+ `../README.md`); the per-template `{{PLACEHOLDER}}` substitution work
lands in a follow-up wave.

Until templates are authored, downstream teams should refer to the
in-repo instance under [`../../../.claude/skills/`](../../../.claude/skills/)
and substitute project-specific identifiers manually.

Planned templates:

- `design-mode.template.md`
- `impl-mode.template.md`
- `verify-mode.template.md`
- `commit-mode.template.md`
- `review-mode.template.md`
- `refresh-defect-archive.template.md`
