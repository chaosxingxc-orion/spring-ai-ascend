# Phase contract templates (placeholder)

This directory will ship the 5 per-phase `*.contract.template.md` files
that downstream platform consumers copy into their governance contracts
directory and customise.

Per [ADR-0098](../../../docs/adr/0098-rc21-scenario-phase-contracts-and-new-discipline-rules.yaml)
§deferred_work, the per-contract template fleshing-out is deferred to
follow-up. rc21 ships the manifest + adoption guide (`../manifest.yaml`
+ `../README.md`); per-contract `{{PLACEHOLDER}}` substitution work
lands in a follow-up wave.

Until templates are authored, downstream teams should refer to the
in-repo instance under [`../../../docs/governance/contracts/`](../../../docs/governance/contracts/)
and adapt section structure + Active Rules table format to their own
rule namespace.

Planned templates:

- `architecture-design.contract.template.md`
- `engineering-implementation.contract.template.md`
- `integration-verification.contract.template.md`
- `system-commit.contract.template.md`
- `review-response.contract.template.md`
