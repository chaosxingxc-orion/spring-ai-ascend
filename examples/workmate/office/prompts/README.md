# office/prompts

Example user prompts for the shipped smoke scripts (no third-party connectors required).

## Public smoke set

| File | Scenario | Script |
|------|----------|--------|
| [prd-draft.md](./prd-draft.md) | Draft a PRD from an outline | `scripts/dogfood/dogfood-prd-draft.sh` |
| [hitl-probe.txt](./hitl-probe.txt) | Permission probe: bash `rm` | `scripts/dogfood/hitl-probe.sh` |

`read-readme-via-mcp.sh` gathers knowledge from a README via MCP and does not use a prompt file
in this directory.

## Internal QA prompts (`*-dogfood.md`)

Files matching `*-dogfood.md` (e.g. `orchestrator-dogfood.md`, `agent-team-dogfood.md`) are
**local team-topology QA prompts** used during milestone sign-off. They are **not** part of the
open-source smoke set and are **not** required to run `dogfood-all.sh` or `make dev`.

When preparing a public release, these files are excluded via `.gitignore` (`office/prompts/*-dogfood.md`).
See [documentation/open-source-release.md](../documentation/open-source-release.md#prompts-and-dogfood-assets).
