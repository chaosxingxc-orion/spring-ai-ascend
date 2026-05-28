---
analysis_id: COMPETITIVE-OPENCODE
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 3
repo_clone_at: D:\ai-research\agent-platforms-survey\opencode\
---

# Competitive Analysis: anomalyco/opencode

Source-grounded analysis at `dev` branch tip cloned 2026-05-28 (default
branch is `dev` per `AGENTS.md:2`). OpenCode is **not** a direct
competitor — it is a **contrast project**: an open-source AI coding
agent shipped as a global Bun/Node CLI with a Tauri/Electron desktop
app. The audience is a single developer, not a multi-tenant
enterprise. The technical stack (Bun + TypeScript + Effect) is
orthogonal to spring-ai-ascend's JVM library shape.

## 1. Tagline & positioning (contrast)

The repo's elevator pitch, verbatim from `README.md:11-12`:

> "The open source AI coding agent."

The README walks through CLI installation (npm/scoop/brew/Arch/Nix —
`README.md:42-60`), desktop app installation (signed `.dmg`, `.exe`,
`.deb`, `.rpm`, `.AppImage` per `README.md:62-78`), and two named
agents at `README.md:108-118`:

> "OpenCode includes two built-in agents you can switch between with the
> `Tab` key.
> - **build** - Default, full-access agent for development work
> - **plan** - Read-only agent for analysis and code exploration
>   - Denies file edits by default
>   - Asks permission before running bash commands
>   - Ideal for exploring unfamiliar codebases or planning changes
> Also included is a **general** subagent for complex searches and
> multistep tasks. This is used internally and can be invoked using
> `@general` in messages."

**Customer profile**: a developer who installs the CLI globally via npm
/ Homebrew / Scoop / Arch pacman / Nix and runs `opencode` against their
local checkout. The optional desktop app adds a GUI. There is also an
enterprise SaaS console (`packages/console/` exists in the repo, plus
`packages/enterprise/`). The dominant onboarding is *single-developer
terminal use*.

**How this differs from spring-ai-ascend**: spring-ai-ascend is a Maven
Central library that JVM apps consume; OpenCode is a global Bun CLI
that wraps a single user's local codebase. OpenCode optimises for
fast cold-start, instant terminal feedback, and rich TUI; ascend
optimises for tenant-isolated long-running server processes. The
substrates (Bun + Effect TS vs JVM 21 + Reactor) and the deployment
shapes (laptop CLI vs library) are orthogonal.

## 2. Architecture skeleton

OpenCode is a Bun monorepo with **23 workspace packages** under
`packages/` (verified by `ls`):

```
packages/
  app/           # web UI app
  console/       # enterprise console (SaaS)
  containers/    # Dockerfiles
  core/          # @opencode-ai/core — shared TS substrate
  desktop/       # Tauri desktop app
  docs/          # docs site
  effect-drizzle-sqlite/  # Effect + Drizzle SQLite adapter
  enterprise/    # enterprise tier
  extensions/    # extensions surface
  function/      # serverless functions
  http-recorder/ # http test recorder
  identity/      # identity service
  llm/           # @opencode-ai/llm — model provider runtime
  opencode/      # the CLI binary
  plugin/        # plugin loader
  script/        # build/dev scripts
  sdk/           # public TS SDK
  slack/         # Slack integration
  stats/         # stats service
  storybook/     # storybook for UI components
  ui/            # shared UI components
  web/           # marketing web site
```

The core agent runtime lives in **`packages/core/src/`**, with files
`agent.ts`, `aisdk.ts`, `catalog.ts`, `event.ts`, `permission.ts`,
`plugin.ts`, `process.ts`, `provider.ts`, `schema.ts`, `session.ts`,
`session-event.ts`, `session-message.ts`, `session-prompt.ts`, `tool-
output.ts`, plus subdirectories `effect/`, `flag/`, `github-copilot/`,
`installation/`, `plugin/`, `util/`. The CLI binary
`packages/opencode/src/` adds 25+ more subdirectories including
`account/`, `acp/` (Agent Client Protocol), `acp-next/`, `agent/`,
`auth/`, `background/`, `bus/`, `cli/`, `command/`, `config/`,
`control-plane/`, `event-v2-bridge.ts`, `file/`, `format/`, `git/`.

LLM providers in `packages/llm/src/providers/` (verified):
`amazon-bedrock.ts`, `anthropic.ts`, `azure.ts`, `cloudflare.ts`,
`github-copilot.ts`, `google.ts`, `openai.ts`, `openai-compatible.ts`,
`openrouter.ts`, `xai.ts` — 10 providers shipped, all OpenAI-shape
or vendor-native.

**Counterpart mapping**: opencode's `packages/core` ≈
spring-ai-ascend's runtime substrate; `packages/llm` ≈ Spring AI's
provider abstraction. There is no Run aggregate counterpart, no
tenant model in the OSS tree (the `packages/enterprise/` directory
exists but the multi-tenant story is closed-source SaaS — the
`packages/console/` directory is the SaaS-companion).

## 3. Developer experience

Install paths per `README.md:42-60`:

```bash
curl -fsSL https://opencode.ai/install | bash      # YOLO
npm i -g opencode-ai@latest                        # npm/pnpm/yarn/bun
scoop install opencode                             # Windows
brew install anomalyco/tap/opencode                # macOS/Linux
sudo pacman -S opencode                            # Arch
nix run nixpkgs#opencode                           # any OS
```

Then `opencode` launches a TUI agent in the current working directory.
The DX is **terminal-first** with optional desktop GUI. Mode switching
via `Tab` between `build` and `plan` agents (`README.md:108-118`).

The contributor's `AGENTS.md:1-3` documents the dev workflow: default
branch is `dev`, regenerate SDK via `./packages/sdk/js/script/build.ts`.
Style rules are strict: avoid `try/catch`, avoid `any`, prefer Bun APIs
(`Bun.file()`), avoid unnecessary destructuring, inline single-use
helpers (`AGENTS.md:9-46`). Conventional commits required:
`feat(scope): summary` (`AGENTS.md:6-8`).

Compared to spring-ai-ascend's "add a Maven dependency, write
`@ConfigurationProperties`, run `mvn spring-boot:run`", OpenCode's
onboarding is "install a global binary, run it in your project". The
*absence of an embedding-library mode* is the key difference — OpenCode
is not designed to be embedded inside another app, it *is* the app.

## 4. Multi-tenancy & governance (contrast)

There is **no tenant model in the OSS tree**. Repository-wide search
finds:

- No `tenantId` / `tenant_id` symbols in `packages/core/src/`.
- The `packages/identity/`, `packages/enterprise/`, and
  `packages/console/` directories hint at a SaaS-managed multi-tenant
  surface, but that surface is in a *separate* runtime layer — not
  the OSS CLI's concern.
- Permission model is **per-session and per-agent**, declared via
  `packages/opencode/src/agent/subagent-permissions.ts`:

> "Build the `permission` ruleset for a subagent's session when it's
> spawned via the task tool. Combines: 1. The parent **agent's**
> edit-class deny rules — Plan Mode's file-edit restriction lives on
> the agent ruleset, not on the session … 2. The parent **session's**
> deny rules and external_directory rules … 3. Default `todowrite`
> and `task` denies if the subagent's own ruleset doesn't already
> permit them." (`subagent-permissions.ts:1-19`)

This is *role-based* permission scoping (which agent can edit which
files / run which tools) — not tenant isolation. The granularity is
per-session, per-agent, per-tool. Defaults are deny-by-default for
`task` and `todowrite` per `subagent-permissions.ts:29-31`.

By contrast, spring-ai-ascend's tenant model (Rule R-C.2.a, Rule R-J.a,
Rule R-J.b) operates at the storage engine + HTTP edge — orders of
magnitude more rigorous because the threat model is *cross-business-
tenant data leakage*, not *cross-agent file-edit accidents*.

## 5. Engine pluggability

OpenCode treats *agent* as a configurable shape with name, model,
permission ruleset, prompt, and tool set — see the `Info` Schema in
`packages/opencode/src/agent/agent.ts:30-50`:

```ts
export const Info = Schema.Struct({
  name: Schema.String,
  description: Schema.optional(Schema.String),
  mode: Schema.Literals(["subagent", "primary", "all"]),
  native: Schema.optional(Schema.Boolean),
  hidden: Schema.optional(Schema.Boolean),
  topP: Schema.optional(Schema.Finite),
  temperature: Schema.optional(Schema.Finite),
  color: Schema.optional(Schema.String),
  permission: Permission.Ruleset,
  model: Schema.optional( ... ),
  ...
})
```

Different agent modes are *configurations*, not *engines*. The
build/plan distinction is a permission-ruleset distinction (plan-mode
denies edits, requires permission for bash). Sub-agents spawned via
the `task` tool inherit parent rulesets per the precedence above.

LLM provider pluggability lives in `packages/llm/src/providers/` —
10 providers ship as a directory of TS files implementing a common
provider contract. New providers add a sibling file. There is no
typed envelope or registry — provider selection is by string id +
discriminated union per the `packages/llm/src/schema/` definitions.

The plugin contract lives at `packages/plugin/` and
`packages/core/src/plugin/`. Plugins are loaded at runtime per
`packages/opencode/src/plugin/`. The ACP (Agent Client Protocol) sub-
package `packages/opencode/src/acp/` is opencode's *external-engine
bridge* — a wire protocol that lets external agents drive opencode's
runtime. The structure is conceptually similar to MCP but opencode-
specific.

## 6. Evolution substrate

OpenCode has *sessions*, *messages*, and *plugins* but no formal
evolution plane. Key surfaces:

- **Session**: `packages/core/src/session.ts`,
  `session-event.ts`, `session-message.ts`, `session-message-
  updater.ts`, `session-prompt.ts`. Sessions hold messages,
  events, and tool outputs.
- **Stats**: `packages/stats/` ships a separate stats service that
  records usage. The `STATS.md` file at the repo root describes the
  shape.
- **Plan/build modes**: per-session `mode` switches the agent's
  permission ruleset. Plan-mode is for analysis; build-mode is for
  modification.
- **Skills**: `Skill` import in `agent.ts:25` references a skill
  abstraction. Skills are prompt-injected guidance.

There is no trajectory store, no fine-tuning loop, no `EvolutionExport`
discriminator. The product is downstream of the model — it consumes
Anthropic / OpenAI / GitHub Copilot / xAI / Cloudflare / Bedrock /
Azure / Google / OpenRouter APIs, it does not train or fine-tune.

Memory is *session-scoped*; cross-session memory is not built-in —
plugin authors can implement persistence themselves via the
`@opencode-ai/core` interfaces. Spring-ai-ascend's
`spring-ai-ascend-graphmemory-starter` plus dedicated `agent-evolve`
plane is a wider bet than OpenCode's session-scoped model.

## 7. Deployment model

OpenCode ships as:

- **Global CLI**: `npm i -g opencode-ai@latest` etc. (`README.md:42-60`).
- **Desktop app**: signed binaries for macOS (arm64 + x64), Windows,
  Linux (deb / rpm / AppImage). Built with Tauri per
  `packages/desktop/` (`README.md:65-78`).
- **Enterprise SaaS console**: `packages/console/` — a managed
  multi-tenant offering hosted at `opencode.ai`.
- **Open-source self-host**: implied via `packages/enterprise/` but
  not the primary distribution.

Build system is **Bun + Turbo + SST** — `bun.lock`, `turbo.json`,
`sst.config.ts` at the repo root. Bun is the canonical runtime
(`packageManager: "bun@1.3.14"` in `package.json:7`).

**No Chinese-silicon support.** Repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero source-code hits. Cross-
platform is by Bun-runtime portability + Tauri desktop builds; there
is no NPU adapter, no ARM64-specific build profile beyond standard
Bun ARM64 binaries.

Distribution shape contrast: spring-ai-ascend is a library consumed
by other JVM apps; OpenCode is a self-contained binary that owns its
process. Both are valid; they target opposite poles of the
embed-vs-app spectrum.

## 8. License + corporate sponsor

License: **MIT** (`LICENSE:1`, "Copyright (c) 2025 opencode"). The
core packages are MIT; the `packages/enterprise/` directory exists
but its license headers were not inspected as the directory may be
empty or stub at this commit.

Corporate sponsor: **anomaly.co** (GitHub org owner — `anomalyco/
opencode`). The project is published on npm as `opencode-ai`.
Distribution channels span Homebrew tap (`anomalyco/tap/opencode`),
Scoop bucket, Arch AUR, Nix nixpkgs, Mise, plus signed desktop
binaries. The cadence + tap multi-channel strategy implies a
well-funded company behind the project.

By contrast, spring-ai-ascend's positioning depends on Huawei's
sovereignty narrative. Both ship permissive licenses, but the
incentive structure differs: anomaly.co is monetising through SaaS +
enterprise console; ascend monetises through being the canonical
SAA-equivalent for the Ascend+Kunpeng platform.

## 9. What we LEARN

Patterns worth absorbing:

1. **Mode-as-permission-ruleset** —
   `subagent-permissions.ts:20-32` shows build/plan modes implemented
   as differential permission rulesets (plan denies edits + asks for
   bash; build allows both). The precedence ladder (agent rules →
   session rules → defaults) is clean. Ascend's posture (`dev`/
   `research`/`prod`) is conceptually similar but at config-knob
   granularity; per-session mode switching is a tighter loop that
   ascend could mirror for interactive sessions.

2. **Subagent inheritance with explicit overrides** —
   `deriveSubagentSessionPermission` (`subagent-permissions.ts:20`)
   has a comment block citing GitHub issue `#26514` documenting the
   class of bug where subagent sessions silently bypassed parent
   agent rules. Inheritance with explicit forwarding is a real
   pattern worth emulating in ascend's hook-point design — child
   hooks should inherit parent denies, not start fresh.

3. **Effect-TS for runtime composition** — `agent.ts:22-24` imports
   `Effect, Context, Layer` from `effect`. The Effect TS library
   gives opencode dependency-injection and structured concurrency.
   The conceptual analogue in ascend is Spring's DI + Reactor; the
   Effect pattern is worth studying as the JS-ecosystem peer.

4. **Permission ruleset as data, not code** —
   `Permission.Ruleset` is an array of `{permission, pattern, action}`
   tuples (`subagent-permissions.ts:25-31`). Declarative permissions
   make audit easy. Ascend's Rule R-L sandbox-policies.yaml is
   already in this shape; making the per-skill ruleset also
   data-driven (not code-driven) would close the audit gap.

5. **Ten LLM providers with shared options** —
   `packages/llm/src/providers/openai-compatible-profile.ts` plus
   `openai-options.ts` lets multiple providers share OpenAI-shape
   options. Ascend's provider layer (delegated to Spring AI upstream)
   should expose a similar "compat profile" abstraction so a custom
   provider can declare itself OpenAI-compatible without duplicating
   plumbing.

6. **STATS.md as the usage-truth surface** — opencode ships a
   top-level `STATS.md` documenting telemetry. Ascend's docs do not
   yet have a single source for "what telemetry the runtime emits";
   adding one (referencing `docs/contracts/run-event.v1.yaml`) would
   close a transparency gap.

7. **Multi-distribution-channel publishing** — opencode ships through
   npm, Homebrew tap, Scoop, Arch AUR, Nix, Mise, and signed desktop
   binaries — *seven distribution channels*. Ascend ships only via
   Maven Central; even though the audience differs, expanding
   distribution to include at least Docker Hub + a Helm chart is on
   the roadmap and opencode's breadth is a useful target.

## 10. Where we DIFFER

| # | Dimension | OpenCode evidence | spring-ai-ascend evidence |
|---|-----------|-------------------|---------------------------|
| 1 | **Runtime substrate** — OpenCode: Bun 1.3.14 + TypeScript + Effect + Tauri. Ascend: JVM 21 + Spring WebFlux + Reactor. | `package.json:7` (`packageManager: bun@1.3.14`) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (Java 21) |
| 2 | **Tenant model in OSS** — OpenCode: no tenant model in OSS tree; multi-tenancy is SaaS-only. Ascend: tenantId NOT NULL on every Run + RLS in OSS. | grep `tenant` in `packages/core/src/` returns zero hits | Rule R-C.2.a + Rule R-J.a |
| 3 | **Embed model** — OpenCode: standalone CLI/desktop binary, not an embeddable library. Ascend: Maven library consumed by JVM apps. | `package.json` (binary build via `packages/opencode/`), Tauri desktop | `spring-ai-ascend-dependencies/pom.xml` BoM |
| 4 | **Permission model granularity** — OpenCode: per-session, per-agent, per-tool. Ascend: per-tenant, per-skill, posture-aware. | `subagent-permissions.ts:1-32` | Rule R-L sandbox-policies.yaml + Rule R-K skill-capacity.yaml |
| 5 | **Hardware sovereignty** — OpenCode: x86_64 + arm64 generic, signed desktop binaries. Ascend: Ascend NPU + Kunpeng ARM64. | grep `Ascend\|Kunpeng` zero hits | Rule R-I five-plane |
| 6 | **Engine pluggability** — OpenCode: Effect TS Layer composition + provider directory + ACP wire protocol. Ascend: typed `EngineEnvelope` + `EngineRegistry`. | `packages/llm/src/providers/*.ts`, `packages/opencode/src/acp/` | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 7 | **Durability shape** — OpenCode: session-scoped in-memory + plugin-configurable persistence. Ascend: Run aggregate + idempotency + per-DB checkpoint backends. | `packages/core/src/session*.ts` | Rule R-C.2.b + Flyway migrations |
| 8 | **Posture / fail-closed** — OpenCode: no posture concept; one default config per install. Ascend: every config knob declares `dev/research/prod` defaults. | `bunfig.toml`, no posture | Rule D-6 PostureBootGuard |
| 9 | **Distribution channels** — OpenCode: 7 channels (npm, Homebrew, Scoop, Arch, Nix, Mise, desktop binaries). Ascend: Maven Central only. | `README.md:42-60` | `pom.xml` (Maven Central) |
| 10 | **Governance enforcement** — OpenCode: oxlint + turbo typecheck + conventional commits + style guide. Ascend: 144+ gate rules, ArchUnit, ADR-driven, recurring-defect ledger. | `package.json:16` (`"lint": "oxlint"`), `AGENTS.md:9-46` | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` |

**Closing positioning note**: OpenCode is the closest opensource peer to
Anthropic's Claude Code or OpenAI's Codex CLI — a single-developer
terminal coding agent. The substrate is Bun + TypeScript + Effect.
The customer is a developer running it on their own machine. None of
the seven coding-agent tools in this tranche is JVM. OpenCode's
breadth (10 providers, 23 packages, 7 distribution channels) confirms
the velocity-advantage that JS/Bun has in the consumer agent space —
and the inverse: that the *enterprise JVM* gap (tenant isolation,
audit-grade Run spine, sovereign-hardware deployment) is genuinely
un-served by these tools. Spring-ai-ascend's positioning is
defensible without competing on OpenCode's axes.
