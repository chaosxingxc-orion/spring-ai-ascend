---
level: L1
view: development
module: agent-service
status: proposed
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.6. Anchors back to canonical Translation & Tool-Intercept (Layer 5b per ADR-0140) in ../logical.md §1 — distinct from Layer 4 RuntimeMiddleware (composition is allowed; identity collapse is forbidden per ADR-0140)."
---

# Translation & Tool-Intercept — Feature Inventory (AS-L1-F40..F47)

> Module: Translation & Tool-Intercept (Layer 5b per ADR-0140).
> Sovereign for: context projection + prompt construction, context compaction boundary, model profile normalization, structured output + result interpretation, ChatAdvisor / tool shaping, client-hosted skill payload shaping, remote Agent payload normalization, tool / memory / retrieval invocation profile.
> Does NOT own: Run aggregate state, runtime governance (Layer 4), engine dispatch (Layer 5a). ChatAdvisor lives here; RuntimeMiddleware lives in Layer 4 — they compose but are not interchangeable.

| Feature ID | Category | Covered clusters | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F40 | Context projection and prompt construction | AS-SC07, AS-SC08 | Convert Session context, variables, memory projection, and compressed summary into InjectedContext, then render model input through PromptTemplate. | Inputs: Session projection, context version, template variables. Outputs: InjectedContext, RenderedPrompt. | Session & Task Manager, Engine Dispatch & Execution. | Stale context, missing variable, prompt overflow, cross-tenant memory read. | Spring AI PromptTemplate, OpenAI Agents sessions, AgentScope state externalization. |
| AS-L1-F41 | Context compaction boundary | AS-SC08, AS-SC24 | Execute summary / trimming / retrieval projection for oversized context while separating compacted output from original Session facts. | Inputs: messages, context limit, memory policy. Outputs: compacted projection, loss marker, projection version. | Session & Task Manager, Task-Centric Control Layer. | Compression loses required fact, stale summary, memory mutation race. | LangGraph state snapshot, CrewAI memory scopes. |
| AS-L1-F42 | Model profile normalization | AS-SC19, AS-SC24 | Normalize provider, model id, options, streaming, structured output, and cost / quota tags into a governable service-local invocation profile. | Inputs: model config, run config snapshot. Outputs: normalized model invocation profile. | Task-Centric Control Layer, Engine Dispatch & Execution. | Unsupported option, provider drift, quota mismatch, profile bypass. | Spring AI ChatClient, Semantic Kernel AI service selector. |
| AS-L1-F43 | Structured output and result interpretation | AS-SC01, AS-SC09, AS-SC13 | Convert model or engine output into typed domain object, tool result, client callback request, or Run terminal payload. | Inputs: model output, schema, tool result. Outputs: typed result, conversion error, terminal payload. | Engine Dispatch & Execution, Task-Centric Control Layer. | Schema invalid, partial output, tool result mismatch, callback payload invalid. | Spring AI StructuredOutputConverter, LangChain4j structured output. |
| AS-L1-F44 | ChatAdvisor / tool shaping | AS-SC13, AS-SC14, AS-SC22 | Decorate requests, shape tool calls, and interpret responses at the model-call boundary; compose with RuntimeMiddleware without replacing it. | Inputs: ChatClient request, tool definition, model response. Outputs: shaped request, tool-call descriptor, interpreted response. | Task-Centric Control Layer, Engine Dispatch & Execution, agent-middleware. | Advisor short-circuit conflicts with runtime policy, tool-call escape, model-call exception. | Spring AI ChatAdvisor, LangChain4j ToolExecutor. |
| AS-L1-F45 | Client-hosted skill payload shaping | AS-SC13, AS-SC14, AS-SC21 | Translate model / agent client-skill intent into schema-bound S2C payload and interpret client responses. | Inputs: tool-call intent, client skill schema, client response. Outputs: S2C request payload, validated response, conversion error. | Access Layer, Task-Centric Control Layer, Internal Event Queue. | Invalid client response, permission mismatch, callback timeout. | A2A input_required, OpenAI Agents tool approval. |
| AS-L1-F46 | Remote Agent payload normalization | AS-SC15, AS-SC16, AS-SC20 | Normalize third-party Agent / peer Agent protocol payloads, statuses, tool results, and error envelopes into service-local remote invocation results. | Inputs: remote response, adapter schema, AgentCard metadata. Outputs: normalized remote result, remote error classification. | Engine Dispatch & Execution, Task-Centric Control Layer, Session & Task Manager. | Remote schema drift, unknown error envelope, resume token mismatch. | A2A task event, AgentScope protocol handler. |
| AS-L1-F47 | Tool / memory / retrieval invocation profile | AS-SC08, AS-SC22 | Normalize tool schema, memory read/write, retrieval, embedding, and sandbox-sensitive invocation forms while passing policy-sensitive decisions to RuntimeMiddleware. | Inputs: tool schema, memory policy, retrieval request. Outputs: normalized tool invocation, memory projection request. | Task-Centric Control Layer, Engine Dispatch & Execution, agent-middleware. | Tool schema drift, memory over-read, sandbox bypass, retrieval tenant leak. | LangChain4j tools/RAG, Spring AI tools, Semantic Kernel plugins. |

## Cross-references

- **Canonical Layer 5b definition**: [`../logical.md`](../logical.md) §1 (Layer 5b owns Translation & Tool-Intercept per ADR-0140 split — distinct from Layer 4 RuntimeMiddleware) + §11 (orthogonality red-line: RuntimeMiddleware ≠ ChatAdvisor).
- **Scenario anchors**: [`../scenarios.md`](../scenarios.md) AS-SC07-AS-SC08 (context recovery + compaction), AS-SC13-AS-SC14 (client-hosted skill), AS-SC15-AS-SC16 (third-party), AS-SC19, AS-SC22, AS-SC24 (configuration).
- **S2C envelope contract**: `docs/contracts/s2c-callback.v1.yaml` (Rule R-M.d).
- **SPI 4-way parity**: [`../spi-appendix.md`](../spi-appendix.md) — `ContextProjector`, `ChatAdvisor`, `PromptTemplate` (design_only for shipped status), `StructuredOutputConverter`, `ModelGateway`.

## Originating source

This file absorbs §6.6 of [`docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md`](../../../logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md).
