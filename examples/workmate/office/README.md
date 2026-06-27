# office

File-based configuration: experts, skills, playbooks, the welcome page, and curated team examples.
This directory is the single source of truth the backend loads at startup (and hot-reloads via Studio).

## Expert packages

```
experts/
├── content-writer/   # content creation
│   ├── expert.yaml
│   └── prompt.md
├── prd-writer/       # PRD drafting
│   └── …
├── fund-analyst/     # remote-MCP fund research
│   └── …
└── …
experts-market/
└── gpt-researcher-team/   # team example (multi-member sequential collaboration)
    ├── lead-prompt.md       # expertType: team + members[]
    └── …                    # member dirs: gpt-researcher-team__<member>
```

- Metadata lives in `expert.yaml` (`id`, `name`, `defaultInitPrompt`, `skillCompatibility`).
- **Teams**: `expertType: team` + `members[]` (each `id`/`name`/`expertId` references a member expert).
- Persona text: `prompt.md` or `lead-prompt.md`, injected into the agent's `workmate_expert` block.
- Experts **do not embed MCP** (ADR-003); the MCP allowlist is configured in `workmate-api`.

API: `GET /api/v1/experts`; pass `expertId` when creating a session.

## Directory layout

```
office/
├── experts/              # hand-written experts (take precedence)
├── experts-market/       # team / marketplace expert examples
├── skills/               # skill packs (SKILL.md + resources)
├── connectors-market/    # connector examples (github, notion — see OSS guide below)
├── playbooks/            # home / marketplace playbook config
├── prompts/              # scenario prompt templates
└── welcome.yaml          # welcome-page config
```

`ExpertRegistry` / `SkillRegistry` / `ConnectorRegistry` load the hand-written and `*-market`
directories in order (same id → hand-written wins). At runtime `McpConnectorResolver` maps a
connector's `mcp.json` to the standard MCP transports (stdio, streamable-http, SSE).

> The full third-party marketplace caches (`market/`, `skills-market/`, `_sources/`, …) are not
> published with the open-source repo — only a small curated set of examples is kept. Your working
> tree may contain dozens of extra `connectors-market/` or `experts-market/` directories after a
> local marketplace pull; `.gitignore` excludes them from git. See
> [documentation/open-source-release.md](../documentation/open-source-release.md#what-ships-in-git).

### Connectors market (open-source subset)

Only **`github`** and **`notion`** are whitelisted for the public repo. They include example
`connector.yaml`, `mcp.json`, and (for Notion) workflow skill docs. Third-party names and icons
are illustrative integration examples, not endorsements.

Other connector directories you see locally (enterprise/iOA integrations, regional SaaS, …) are
**not** part of the open-source release and must not be committed.

## Welcome page & playbooks

- **Welcome page**: `GET /api/v1/welcome` — aggregates `welcome.yaml` and mounts playbooks by `placement`.
- **Playbooks**: `GET /api/v1/playbooks?placement=home-best-practice|market-featured`.
- Editing copy/cards/scenario chips takes effect after a Studio reload or API restart — no frontend change.

## Scenario prompts

`prompts/` — example prompts for the shipped smoke scripts (see `scripts/dogfood/`).

### Public smoke set

| File | Scenario | Script |
|------|----------|--------|
| [prd-draft.md](./prd-draft.md) | Draft a PRD from an outline | `scripts/dogfood/dogfood-prd-draft.sh` |
| [hitl-probe.txt](./hitl-probe.txt) | Permission probe: bash `rm` | `scripts/dogfood/hitl-probe.sh` |

(MCP README reading uses `scripts/dogfood/read-readme-via-mcp.sh` without a dedicated prompt file.)

### Internal QA prompts (`*-dogfood.md`)

Eight files such as `orchestrator-dogfood.md`, `message-bus-dogfood.md`, … are **milestone
team-topology QA prompts**. They are **not** used by the open-source smoke scripts in
`scripts/dogfood/dogfood-all.sh`.

When preparing a public release, these files are **gitignored** (`office/prompts/*-dogfood.md`) and
will not appear in the public tree. End users do not need them to run WorkMate.

See [documentation/open-source-release.md](../documentation/open-source-release.md#prompts-and-dogfood-assets).
