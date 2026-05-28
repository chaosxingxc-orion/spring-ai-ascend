# Onboarding — Enterprise Agent Architect (Persona-E)

> Read time: ~5 minutes. After this, you understand the Engine Contract substrate, the evolution plane, and how framework choice stays reversible.

You are the **architecture persona** for `spring-ai-ascend`. The platform was built assuming your background: framework-choice decisions across multiple business agents; observability + cost + governance reasoning; familiarity with the LLM application landscape (LangGraph / AutoGen / CrewAI / ReAct / hierarchical orchestration). You're the bridge between Persona-C (developer) and Persona-D (operator), and the primary owner of evolution strategy.

## What this platform is, from your perspective

`spring-ai-ascend` is **the substrate that hosts heterogeneity** — multiple agent frameworks (graph-state, ReAct, supervisor-worker, debate, external SDK adapters), multiple LLM providers (OpenAI, Anthropic, local Ascend-hosted, Kunpeng-served open-source), multiple orchestration patterns — all governed uniformly via the Engine Contract envelope. It is **not yet-another-framework**. It is the runtime + governance kernel that makes framework choice reversible per agent.

Authoritative product framing: `product/PRODUCT.md`. Your persona: `product/personas.yaml` → Persona-E. Your primary product claims: **PC-004** (Multi-framework substrate) and **PC-005** (Self-evolving agents — the irreplaceable differentiator).

## The Engine Contract — the substrate spec

Every Run dispatches through `EngineRegistry.resolve(envelope)` against the schema in `docs/contracts/engine-envelope.v1.yaml`. Pattern-matching on `ExecutorDefinition` subtypes outside the registry is **forbidden** (Rule R-M.a). The envelope is framework-agnostic — adding a new engine type means writing an `ExecutorAdapter` registered under that type, not patching the core.

Strict matching (Rule R-M.b): a Run declaring `engine_type=X` executes only via the adapter for X; mismatch raises `EngineMatchingException` and transitions the Run to FAILED with reason `engine_mismatch`. No framework leakage across types.

Cross-cutting policies (cost, identity, observability, sandbox routing, model gateway, checkpoint, safety guardrails) are expressed as `RuntimeMiddleware` listening on canonical `HookPoint` events (`docs/contracts/engine-hooks.v1.yaml`). Same policy applies whether the engine is LangGraph-style or ReAct-style. Rule R-M.c.

S2C callbacks (server-to-client capability invocation) for client-side data access flow through `S2cCallbackEnvelope` + `S2cCallbackTransport` SPI (Rule R-M.d). Waiting Runs suspend via `SuspendSignal.forClientCallback(...)` and resume when client returns; validated against `docs/contracts/s2c-callback.v1.yaml`.

## What v1.0 ships in PC-004 substrate

v1.0 (2026-06-30) ships:
- **One engine adapter** — graph-state engine on top of an extended Spring AI ChatClient
- **One model provider** — OpenAI-compatible interface (works against OpenAI, local Ascend-hosted models exposing OpenAI-compatible API, Kunpeng-served open-source models via the same interface)
- **Engine envelope contract v1** — `docs/contracts/engine-envelope.v1.yaml`
- **Hook taxonomy** — `docs/contracts/engine-hooks.v1.yaml`

v1.1+ weekly cadence ships additional adapters: ReAct (W+3), external SDK adapters (W+5+), MCP bridge (W+2), additional model providers via Spring AI ChatClient integrations.

## The evolution plane — PC-005

Five-plane topology (P-I) reserves a dedicated `evolution` plane for self-evolution. Three sub-systems, each with an open SPI:

1. **Knowledge substrate (RAG / vector)**: `graphmemory-starter` + Spring AI `VectorStore` abstraction; adapters for Qdrant / Milvus / Weaviate / pgvector. Ingestion + chunking + re-embedding pipelines.
2. **Memory substrate (episodic / semantic / procedural)**: per-agent per-tenant long-term state. Memory SPI; adapters for MemGPT / Letta / mem0 backends.
3. **Skill substrate**: skills as versioned governed assets; success-rate tracking per (skill, tenant, agent) triple; lifecycle tied to Rule G-14 9-state lifecycle (`proposed → ... → shipped → deprecated → removed`).

RL trajectory export: every Run emits `(state, action, reward, next_state)` tuples. Scope respects Rule R-M.e (`EvolutionExport ∈ IN_SCOPE | OUT_OF_SCOPE | OPT_IN`). Tenant-isolated by default. Open-format export for HuggingFace TRL / OpenAI fine-tuning JSONL / internal frameworks.

**Honesty note**: the evolution plane is **largely `design_only` at v1.0**. Most components ship progressively in v1.5+ per Phase B cluster cycles. PC-005 declares the DIRECTION; you are the persona who drives the rollout sequence.

## Framework + model selection — your daily decisions

For each agent class your team builds:

1. **Engine choice**: graph-state (for deterministic workflows with branching), ReAct (for tool-using exploration), supervisor-worker (for hierarchical decomposition), debate (for consensus-required tasks).
2. **Model choice**: GPT-class for high-reasoning; local Ascend-served for cost / sovereignty / latency; specialized open-source on Kunpeng for niche tasks.
3. **Memory strategy**: stateless / episodic / semantic — informed by business cost of memory leakage vs context-engineering complexity.
4. **Evolution opt-in**: which agents export trajectories for RL? Which knowledge feeds the corpus? Skill versioning policy.

The framework + model + memory + evolution choices are **reversible per agent** because the Engine Contract envelope decouples them.

## When you design — phase-contract skills

- **`/design-mode`** — when proposing a new ADR / module spec / SPI declaration. Loads `docs/governance/contracts/architecture-design.md` which surfaces L0 constraints + relevant rules.
- **`/review-mode`** — when processing reviewer findings or sweeping for corpus drift.

## v1.0 financial-vertical architecture choices

For the v1.0 financial reference agent (`loan-review-assistant`):
- Engine: graph-state (deterministic flow: gather → analyze → summarize)
- Model: configurable per deployment; demos use OpenAI-compatible interface
- Memory: stateless (each Run is independent for v1.0 compliance simplicity)
- Skills: bank-internal API skills (CIF, transaction-history, credit-bureau gateway) wired via Spring `WebClient`
- Sandbox: `financial_default` policy
- Audit: full RunEvent + tool call + model invocation persistence

Future v1.x agents may opt into memory + evolution; v1.0 stays simple to clear compliance review.

## Other personas

- `docs/onboarding/developer.md` — Persona-C (Agent Developer)
- `docs/onboarding/sre.md` — Persona-D (Production Operator)
- `docs/onboarding/compliance-reviewer.md` — Persona-F (Compliance / Risk Officer) — primary v1.0 persona
