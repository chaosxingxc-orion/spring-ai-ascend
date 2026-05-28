---
analysis_id: COMPETITIVE-GUARDRAILS
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 4
repo_clone_at: D:\ai-research\agent-platforms-survey\guardrails\
---

# Infrastructure Analysis: guardrails-ai/guardrails

Source-grounded analysis at commit
`91b038e1f9f40acea1af120bd1d797e0ab4c5ec4` (2026-05-26, last commit
`Merge pull request #1490 from guardrails-ai/ShreyaR-patch-2`). Python
`pyproject.toml:3` declares `version = "0.10.0"`. Guardrails (the
`guardrails-ai/guardrails` project) is a Python framework for
**structured-output validation + risk mitigation** in LLM applications.
Where NeMo-Guardrails is **flow-shaped** (Colang dialog rails),
Guardrails-AI is **schema-shaped** (validate the LLM's output against a
JSON schema with pluggable validators). This is squarely
**infrastructure** for our Phase C purposes and maps to PC-003 (safety
+ audit trail).

## 1. Tagline & positioning

Guardrails-AI is **infrastructure** — we integrate via SPI, we do not
compete. README pitch (`README.md:26-29`):

> "Guardrails is a Python framework that helps build reliable AI
> applications by performing two key functions:
> 1. Guardrails runs Input/Output Guards in your application that
>    detect, quantify and mitigate the presence of specific types of
>    risks.
> 2. Guardrails help you generate structured data from LLMs."

The project's intellectual cornerstone is **Guardrails Hub** — a
registry of pre-built validators that users compose into `Guard`
objects. From `README.md:34-37`:

> "Guardrails Hub is a collection of pre-built measures of specific
> types of risks (called 'validators'). Multiple validators can be
> combined together into Input and Output Guards that intercept the
> inputs and outputs of LLMs."

The Hub claim from `README.md:23`:

> "**[Feb 12, 2025]** We just launched Guardrails Index — the first of
> its kind benchmark comparing the performance and latency of 24
> guardrails across 6 most common categories!"

For spring-ai-ascend, Guardrails-AI sits in `compute_control` as a
**per-request validator** wrapping model calls; it is not a substrate
(no durable state) but a synchronous decision-and-rewrite gate. It
complements NeMo-Guardrails: NeMo for flow-shaped policies,
Guardrails-AI for structured-output validation + per-field risk
detection.

## 2. Architecture skeleton

The `guardrails/` package decomposes into ~30 sub-packages
(`ls guardrails/`):

- **`guard.py`** — `Guard` is the main entry-point class
  (`guardrails/guard.py:1-60`). Composes a schema + validators +
  optional LLM call. Subclasses: `AsyncGuard` (`async_guard.py`).
- **`validator_base.py`** — `Validator` base class
  (`guardrails/validator_base.py:1-50`) defining the validator contract:
  produces `PassResult` / `FailResult` / `ValidationResult`. Validators
  live remotely on the **Hub** or as local Python modules.
- **`hub/`** — Hub client + registry integration. Validators are
  installed via `guardrails hub install hub://guardrails/profanity_free`.
- **`validators/`** — currently empty package (`guardrails/validators/__init__.py`
  only) — validators are pulled from Hub at install time, not vendored.
- **`run/`** — `Runner` + `StreamRunner` (request-execution engine).
- **`schema/`** — JSON schema, RAIL schema, Pydantic schema loaders
  (`primitive_schema.py`, `pydantic_schema.py`, `rail_schema.py`,
  `validator.py`).
- **`actions/`** — actions a validator can take on failure (`reask`,
  `fix`, `filter`, `refrain`, `noop`).
- **`llm_providers.py`** — LLM provider abstraction (`get_llm_ask`,
  `model_is_supported_server_side`).
- **`formatters/`** — output-format converters.
- **`telemetry/`** + **`hub_telemetry/`** — OTel + Hub-side telemetry.
- **`call_tracing/`** — request tracing + history.
- **`document_store.py`**, **`vectordb/`**, **`embedding.py`** — RAG
  support for validators that need it (e.g. factchecking).
- **`integrations/`** — framework adapters (`langchain` visible).
- **`server_ci/`** — hosted-server build configuration.
- **`hub_token/`** — Hub authentication tokens.
- **`stores/`** + **`stores/context.py`**: per-call execution context
  storage. The `set_call_kwargs(...)` / `get_call_kwarg(...)` /
  `set_guard_name(...)` API (visible in `guard.py:55`) propagates
  scope through the validation pipeline via Python contextvars.
  Analogue: our `MDC` (Mapped Diagnostic Context) for SLF4J — same
  pattern, different language.
- **`merge.py`**: structural merge of validation outcomes for
  composite guards. When multiple validators run on the same field
  and some pass while others fail, merge logic decides the overall
  outcome.
- **`async_guard.py`**: `AsyncGuard` is the reactive sibling. For
  our Rule R-G (reactive external I/O) compliance, this is the entry
  point we'd integrate, not the synchronous `Guard`.

Counterpart mapping into spring-ai-ascend's reactor:

| spring-ai-ascend module | Guardrails-AI counterpart | Role |
|---|---|---|
| `agent-middleware` (output validators) | `guardrails/guard.py` + `validator_base.py` | direct mapping — output validation |
| `docs/contracts/*.yaml` (schemas) | `guardrails/schema/{primitive,pydantic,rail}_schema.py` | schema-driven contract enforcement |
| (none — Hub is external SaaS) | `guardrails/hub/` | external validator registry |
| `agent-evolve` (audit) | `guardrails/call_tracing/` | request history |

## 3. Developer experience

Pip install + Guard config:

```bash
pip install guardrails-ai
guardrails configure   # configures Hub token
guardrails hub install hub://guardrails/profanity_free
```

Then Python:

```python
from guardrails import Guard
from guardrails.hub import ProfanityFree
guard = Guard().use(ProfanityFree, on_fail="exception")
result = guard("The model output goes here")
```

For structured output:

```python
from pydantic import BaseModel
class Profile(BaseModel):
    name: str
    email: str
guard = Guard.for_pydantic(Profile)
validated = guard(llm_call_fn, ...)
```

The DX is **schema-as-policy** — the developer defines the desired
output shape (Pydantic / JSON Schema / RAIL XML), composes validators
per field, and the `Guard` wraps the LLM call. On failure, the `Guard`
can `reask` (send a re-prompt with errors), `fix` (programmatic
correction), `filter` (drop offending output), `refrain` (refuse),
or `noop` (pass-through with log). For spring-ai-ascend, the
schema-driven Guard pattern maps cleanly to our
`docs/contracts/*.yaml` contract surfaces — validating LLM outputs
against our Run contract's response schema is the same operation.

## 4. Multi-tenancy & governance

Guardrails-AI is **tenancy-agnostic** — like NeMo-Guardrails, it's a
per-request library, not a stateful service. There is no `tenant_id`
in the schema. Each `Guard` is configured per-application or
per-deployment. Hub validators carry their own authentication
(`hub_token/`, `guardrails configure`), and Hub usage is
metered against the Hub account (`hub_telemetry/`).

For audit: `guardrails/call_tracing/` plus `guardrails/classes/history/`
(visible as `Call`, `CallInputs` imports in `guard.py:43-44`) preserve
per-request history. This is **the closest analogue in Tranche-4 to
our Run-event spine** — every Guard invocation produces a `Call` record
with inputs, validators run, results, durations. We could ingest these
into our `RunEvent` topic directly.

For our R-J + R-K compliance, we'd:

1. Instantiate `Guard` per `(tenantId, contractId)` in our
   `agent-middleware` layer.
2. Map each Hub validator invocation to an emitted `GuardrailEvent`
   on our `control` channel (Rule R-E).
3. Cache Hub-token-authenticated validators per tenant (Hub validators
   may incur per-call cost to the Hub-side service).

## 5. Engine pluggability

Guardrails-AI's **validator registry** (`guardrails/hub/`) is its
extension surface. Each validator implements
`Validator` (from `validator_base.py`) with:

- `validate(value, metadata) -> PassResult | FailResult`
- Optional `chunk_validate(...)` for streaming.
- `on_fail` policy (`OnFailAction.{reask,fix,filter,refrain,noop}`).

Validators are loaded by hub-style URI: `hub://guardrails/<name>`. The
Hub registry (`guardrails/hub/registry.py` — referenced in
`validator_base.py:24` as `from guardrails.hub.registry import
get_registry`) maps URIs to installed Python modules. Adding a custom
validator means writing a subclass and registering it (locally or on
the Hub).

For our `EngineRegistry` (R-M.a), the Guardrails-AI validator registry
is a **clean SPI shape** — typed `validate()` contract, typed
`PassResult`/`FailResult` returns, declarative `on_fail` policy. Our
`GuardrailSpi` should adopt the same `PassResult`/`FailResult`
discrimination + `OnFailAction` enum.

## 6. Evolution substrate

Guardrails-AI is **not an evolution substrate**, but contributes:

- **Call history** (`guardrails/classes/history/`): structured
  `Call`+`CallInputs` records per Guard invocation. Field set includes
  raw inputs, prompt, validator results, fix/reask outcomes, timing.
- **Validation history** (`guardrails/classes/validation/`):
  `ValidationSummary` aggregates per-validator pass/fail across a run.
- **Streaming validation** (`StreamRunner` in `guardrails/run/`):
  per-chunk validation with mid-stream rejection. Validators can
  consume chunks and reject the stream early, saving model tokens.
- **Reask loop**: when a validator fails with `on_fail=reask`, Guardrails
  automatically re-prompts the LLM with the validation errors attached.
  This is an **implicit evolution loop** — the LLM learns within-session
  to produce schema-compliant output.

The reask mechanism is a substrate-adjacent contribution. For our
`agent-evolve` plane, ingesting reask records would let us identify
schemas/validators that produce frequent reasks and trigger trajectory-
training runs to improve the model's first-shot compliance.

Additional substrate-adjacent contributions:

- **RAIL schema** (`guardrails/schema/rail_schema.py`): Guardrails'
  custom XML-flavored schema language. Predates and influenced the
  more-modern Pydantic-driven approach. **We don't adopt RAIL** —
  Pydantic / JSON Schema / our `docs/contracts/*.yaml` are the right
  modern choices. But the existence of RAIL is a reminder that
  designing a custom schema language is **always wrong** when
  industry-standard alternatives exist.
- **`ErrorSpan`** (`guardrails_ai.types.ErrorSpan`, referenced
  `guard.py:32`): byte-range error annotation. When a validator
  rejects part of a response, it returns the offset+length of the
  offending text. This is **the right shape for partial
  rejection** — much better than "the whole response failed",
  enables surgical corrections.
- **`Stack`** (`guardrails.classes.generic.Stack`, referenced
  `guard.py:42`): generic stack utility used for tracking validator
  invocation history. Not architecturally significant by itself
  but indicates the codebase has thought about deep validation
  pipelines that benefit from a stack-based execution model.
- **Document store** (`guardrails/document_store.py`) +
  **vectordb** (`guardrails/vectordb/`): RAG retrieval for
  validators that need it (e.g. ProvenAI factchecking). Lighter
  than NeMo-Guardrails' kb module but covers the same need.

## 7. Deployment model

Three modes:

1. **Embedded library** — `pip install guardrails-ai`, import `Guard`.
   The primary mode.
2. **Server** (`server_ci/`): hosted-server build, exposes validators
   over HTTP.
3. **Hosted Hub** — `hub.guardrailsai.com` runs validators server-side
   for those marked `model_is_supported_server_side`
   (`guardrails/llm_providers.py:42`). Reduces local model loading cost.

**No Dockerfile** at repo root (verified — `ls` shows no `Dockerfile`).
**No Helm chart**. **No Kubernetes manifests**. Multi-arch: pure Python,
runs on ARM64/Kunpeng. The lack of containerization is a gap — our
adapter would containerize Guardrails-AI ourselves for sidecar
deployment in `bus_state`. Five-plane placement: **embedded** in
`compute_control` (host-app library); **server mode** in `bus_state`
(side-service); **Hub-managed** is an external dependency we'd treat as
a third-party SaaS.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE:1-3`; `pyproject.toml:10-12` —
`license = "Apache License 2.0"`). Corporate sponsor: **Guardrails AI**
(`pyproject.toml:5-7` — `authors = [{name = "Guardrails AI", email =
"contact@guardrailsai.com"}]`). Commercial offering at
`guardrailsai.com`. Permissive license, dependency-safe.

A noteworthy transitive dependency: `pyproject.toml:30` declares
`"litellm>=1.37.14,<1.82.6"` — Guardrails-AI **depends on LiteLLM**
(a Tranche-4 cohort member) as its LLM-provider abstraction. Choosing
Guardrails-AI gives us indirect LiteLLM consumption. Other notable
deps: `langchain-core>=1.0.0,<2.0` (LangChain ecosystem integration),
`tiktoken`, `jsonschema[format-nongpl]`, `pydash`. The version pin
`litellm<1.82.6` means Guardrails-AI is **pinned to an older LiteLLM
than our reference** (1.87.0); choosing both means we have to manage
version-skew at the Python boundary.

## 9. What we LEARN

1. **Validator-as-SPI with typed Pass/Fail returns** —
   `guardrails/validator_base.py` defines a clean
   `validate(value, metadata) -> PassResult | FailResult` contract.
   Our `GuardrailSpi` should adopt the same `Pass`/`Fail` typed
   discrimination + `ErrorSpan` for byte-range error reporting.

2. **OnFailAction taxonomy** — `OnFailAction.{reask,fix,filter,refrain,
   noop}` from `guardrails/types/on_fail.py` (referenced
   `guard.py:56`). This is the **canonical taxonomy of validator
   failure modes** and we should adopt the exact same enum names for
   our `GuardrailFailureAction`.

3. **Reask loop pattern** — automatic re-prompt with validation
   errors attached is a powerful self-correction primitive. Our
   `EngineHook` at `AFTER_MODEL` can implement the same: on
   FailResult, re-issue the model call with corrective context, up to
   N retries, with each retry emitting a `RunEvent` for audit.

4. **Streaming validation** — `StreamRunner` validates per-chunk
   and can reject the stream early. For our R-G compliance
   (reactive external I/O), per-chunk validation in a `Flux<String>`
   pipeline is the right operational shape — emit invalidation
   downstream, cancel upstream, save tokens.

5. **Schema-driven Guard composition** — `Guard.for_pydantic(SomeModel)`
   composes validators per-field from Pydantic type annotations. Our
   Java equivalent would use `@Valid` + custom annotations to compose
   `Guard` per response schema in `docs/contracts/*.yaml`.

6. **Hub registry pattern** — `hub://guardrails/<name>` URI scheme
   for validator lookup. Our `GuardrailRegistry` should follow a
   similar `guardrail://<namespace>/<name>` URI convention so policy
   authors can reference validators by stable identifier.

7. **Index benchmark transparency** — Feb 2025's Guardrails Index
   (`README.md:23`) compares 24 guardrails across 6 categories. This
   level of public benchmarking is the **strongest empiricism story**
   in Tranche 4's safety cohort.

8. **AsyncGuard parallel API** — `guardrails/async_guard.py` provides
   the reactive sibling of `Guard`. For our Rule R-G compliance
   (reactive external I/O), we must integrate via `AsyncGuard` not
   `Guard`. This is a **boundary-discipline reminder**: every
   substrate dependency exposes both sync and async APIs; our
   adapters MUST use the async path.

9. **ErrorSpan byte-range annotation** — partial-rejection encoded
   as `ErrorSpan(start, end, message)`. Adopting this shape in our
   `ValidationOutcome` allows downstream UI surfaces to highlight
   problematic regions of a response instead of "all-or-nothing"
   failure messages. This is a real DX improvement for end-users
   reviewing rejected outputs.

10. **Per-call context propagation via contextvars** —
    `guardrails/stores/context.py` uses Python contextvars to carry
    `call_kwargs`, `guard_name`, scope through the validation
    pipeline. Java's `ThreadLocal` + Reactor's `Context` provide
    the same primitive; our `agent-middleware` should propagate
    `(runId, tenantId, requestId)` similarly so validator
    invocations carry full context for audit.

## 10. Integration surfaces (five-plane placement)

| Surface | What we adapt | spring-ai-ascend SPI | Five-plane home |
|---|---|---|---|
| **Validator contract** | `validate(value, metadata) -> PassResult/FailResult` | `GuardrailValidatorSpi.validate(input, metadata)` returning sealed `ValidationOutcome` | `compute_control` |
| **OnFailAction taxonomy** | `{reask, fix, filter, refrain, noop}` enum | `GuardrailFailureAction` Java enum, same five names | `compute_control` |
| **Reask loop** | Guard auto-re-prompts on FailResult | `EngineMiddleware.reaskOnFailure(maxRetries)` at `AFTER_MODEL` HookPoint | `compute_control` |
| **Streaming validation** | `StreamRunner` per-chunk validation | reactive `Flux<String>` filter at the streaming boundary (Rule R-G compliant) | `compute_control` |
| **Schema-driven Guards** | `Guard.for_pydantic(Model)` | Java records under `docs/contracts/*.yaml` → auto-generated Guard configs | `bus_state` config |
| **Hub URI registry** | `hub://guardrails/<name>` | `guardrail://<namespace>/<name>` URI scheme in our `GuardrailRegistry` | `bus_state` registry |
| **Call history** | `guardrails/classes/history/Call` per-Guard record | map to our `RunEvent` schema; ingest into trajectory store for `agent-evolve` | `evolution` |
| **Server-mode HTTP API** | (no built-in Docker — we containerize) | `GuardrailsAdapter` using Reactive `WebClient` (Rule R-G) | `compute_control` (caller) → `bus_state` (server) |
| **LiteLLM transitive dep** | Guardrails internally uses LiteLLM | our model gateway already deploys LiteLLM; pin compatible version | `bus_state` (LiteLLM) |

**Data shape we adapt**: Guardrails-AI's `ValidationOutcome` →
Java sealed `ValidationOutcome` with `Pass<T>(value, metadata)` and
`Fail(errorSpans, suggestedFix, action)` cases. The `Call` history
record becomes a `GuardrailEvent` on our control channel.

**Operational pattern**: deploy Guardrails-AI as a sidecar in
`bus_state` (we containerize it ourselves since the repo ships no
Dockerfile). Our `agent-execution-engine` invokes it via Reactive
`WebClient` at `AFTER_MODEL` HookPoints (R-M.c). The validator URI
(`guardrail://<namespace>/<name>`) is declared in
`docs/contracts/*.yaml` per response schema, and the engine's
middleware loads the corresponding Guard at startup. Failure actions
follow the canonical taxonomy: `reask` triggers a bounded re-prompt
loop; `fix` applies a programmatic correction; `filter` drops the
offending field; `refrain` returns an error to the caller; `noop`
emits an audit event but proceeds.

**Where it sits**: `bus_state` (server instance) with a synchronous
caller bridge into `compute_control` (the engine's HookPoint
middleware). Combined with NeMo-Guardrails (Tranche-4 cohort
member), they form the two halves of our PC-003 safety story —
NeMo for **dialog-flow rails** (multi-turn policies, jailbreak
detection), Guardrails-AI for **structured-output validation**
(schema compliance, PII detection, toxicity scoring per-field).
Both emit `GuardrailEvent` on the control channel and feed
trajectories into the `agent-evolve` plane for safety-eval.

**LiteLLM-version-skew management**: Guardrails-AI's
`pyproject.toml:30` pins `litellm>=1.37.14,<1.82.6`, while our
LiteLLM reference is 1.87.0. Three options: (a) accept the skew —
deploy Guardrails-AI with an older LiteLLM in its own Python env,
isolated from our gateway LiteLLM; (b) wait for Guardrails-AI to
update its LiteLLM pin; (c) avoid the indirect dependency by
having our adapter call Guardrails-AI without invoking its LLM-call
methods (only the validate methods, which don't use LiteLLM
internally). Option (c) is the cleanest — our adapter passes
already-generated LLM output to Guardrails-AI for validation only;
the LLM call itself goes through our LiteLLM gateway. This
decoupling is **architecturally important** for managing transitive
dependencies between substrate components.

**Validator inventory strategy**: of the Hub's 24+ validators, we
adopt a curated subset declared in `docs/governance/guardrails/
validator-inventory.yaml` per posture (dev/research/prod). The `prod`
posture enables PII detection, toxicity, prompt-injection, and JSON
schema validators by default; the `research` posture adds factchecking
and hallucination detection (which incur model-call cost); the `dev`
posture enables a minimum baseline. Posture-driven validator
activation enforces Rule D-6 (posture-aware defaults).

**Comparison to NeMo-Guardrails**: both projects share the safety
substrate role but differ in shape. NeMo is **flow-shaped** (dialog
turns, multi-step policies); Guardrails-AI is **field-shaped**
(per-output-field validation against a schema). Our integration uses
both: NeMo wraps the entire conversation as a dialog rail enforcer,
Guardrails-AI validates structured outputs after generation. The
canonical sequence per Run step: NeMo validates input → engine calls
model → Guardrails-AI validates output → NeMo validates dialog
context. Both emit events on the `control` channel; we de-duplicate
by `(runId, stepId, validator-uri)` to avoid double-charging cost
attribution.
