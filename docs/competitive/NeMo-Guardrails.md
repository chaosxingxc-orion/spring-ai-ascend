---
analysis_id: COMPETITIVE-NEMO-GUARDRAILS
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 4
repo_clone_at: D:\ai-research\agent-platforms-survey\NeMo-Guardrails\
---

# Infrastructure Analysis: NVIDIA/NeMo-Guardrails

Source-grounded analysis at commit
`d1ee77562224c51b2b7e3aff0e9b6405be79fbf9` (2026-05-26, last commit
`fix(library): detect_regex_pattern() crashes with TypeError during
output streaming (#1932)`). Python `pyproject.toml:7` declares
`version = "0.22.0"`. The README cites version 0.21.0 as the latest
released (`README.md:18`). NeMo-Guardrails is **NVIDIA's open-source
toolkit for programmable LLM safety rails** — input/output filters,
dialog flows, topic constraints, jailbreak detection. This is squarely
**infrastructure** for our Phase C purposes and maps to PC-003 (safety +
guardrails).

## 1. Tagline & positioning

NeMo-Guardrails is **infrastructure** — we integrate via SPI, we do
not compete. README pitch (`README.md:23`):

> "NVIDIA NeMo Guardrails library is an open-source toolkit for easily
> adding *programmable guardrails* to LLM-based conversational
> applications. Guardrails (or 'rails' for short) are specific ways of
> controlling the output of a large language model, such as not talking
> about politics, responding in a particular way to specific user
> requests, following a predefined dialog path, using a particular
> language style, extracting structured data, and more."

The project's intellectual cornerstone is **Colang**, a domain-specific
language for describing flows of guarded LLM interaction. `nemoguardrails/colang/`
contains both v1.0 and v2.x grammars. Colang is to dialog flows what
regex is to text patterns — a compact, expressive way to say "if the
user does X, the bot should respond Y, but never let the model say Z".
Five-plane placement: NeMo-Guardrails sits in `compute_control`
as a **request-pipeline filter** wrapping the model call; it is not a
substrate (it has no durable state), it is a synchronous gate.

## 2. Architecture skeleton

The `nemoguardrails/` package decomposes into roughly 24 sub-packages
(`ls nemoguardrails/`):

- **`colang/`** — Colang language runtime (`runtime.py`) + v1.0 grammar
  + v2.x grammar (`nemoguardrails/colang/v1_0/`, `v2_x/`). Colang
  parsed into flows that drive the rails.
- **`rails/llm/`** — `LLMRails` class
  (`nemoguardrails/rails/llm/llmrails.py:135`) is the main entry point.
  Loads a `RailsConfig` (config.yml + .co files), wraps an underlying
  LLM, exposes `generate()` / `generate_async()`.
- **`actions/`** — built-in actions Colang can invoke (e.g.
  `retrieve_relevant_chunks`, `generate_user_intent`, `generate_bot_message`).
- **`library/`** — **27 third-party guardrail integrations**
  (`ls nemoguardrails/library`): activefence, ai_defense, attention,
  autoalign, clavata, cleanlab, content_safety, crowdstrike_aidr,
  factchecking, fiddler, gcp_moderate_text, gliner, guardrails_ai
  (Guardrails-AI cohort member integration), hallucination,
  injection_detection, jailbreak_detection, llama_guard, pangea,
  patronusai, policyai, privateai, prompt_security, regex, etc.
- **`llm/`** — provider clients + prompts + filters
  (`nemoguardrails/llm/providers/`, `nemoguardrails/llm/prompts/`).
- **`kb/`** — knowledge-base RAG support.
- **`tracing/`** + **`telemetry.py`** — OpenTelemetry instrumentation.
- **`server/`** — FastAPI server exposing rails via HTTP.
- **`integrations/langchain/runnable_rails.py`** — LangChain `Runnable`
  adapter (verified existence; first lines visible).
- **`evaluate/`** + **`eval/`** + **`benchmark/`** — built-in
  evaluation framework (one of the deepest in the cohort).
- **`guardrails/`** (in-tree, distinct from the upstream
  guardrails-ai project): `engine_registry.py`, `base_engine.py`,
  `api_engine.py`, `guardrails.py`, `iorails.py`. This is NeMo's
  **engine-abstraction** for guardrail composition — a registry where
  different rail engines (Colang, regex, embedding-based) plug in
  behind a common interface. Structurally **closer** to our
  `EngineRegistry` (Rule R-M.a) than any other piece of code in
  Tranche 4 — same name, same purpose, comparable scope.
- **`async_work_queue.py`**: explicit async work queue for rail
  execution. Indicates rails run as concurrent tasks, not blocking
  synchronous calls.

Counterpart mapping into spring-ai-ascend's reactor:

| spring-ai-ascend module | NeMo-Guardrails counterpart | Role |
|---|---|---|
| `agent-middleware` (guardrail hooks) | `nemoguardrails/rails/llm/llmrails.py` | direct mapping — guardrail dispatch |
| (none — Colang is policy DSL we'd consume) | `nemoguardrails/colang/` | external policy artifact |
| (none — we don't ship rails ourselves) | `nemoguardrails/library/` | adapter library we'd embed |
| `agent-execution-engine` | `nemoguardrails/actions/` | actions invoked from flows |

## 3. Developer experience

NeMo-Guardrails' DX has two distinct surfaces: **policy authoring**
(write `.co` files, configure `config.yml`) and **runtime integration**
(import `LLMRails`, call `generate()`). The policy authoring surface
is unusual because Colang is a **DSL** rather than YAML or Python —
this is intentional. The argument for Colang: dialog flows have a
specific structure (intent → flow → response) that benefits from a
purpose-built notation; expressing the same in YAML or imperative
Python is verbose and error-prone. The argument against: developers
must learn a new language, IDE tooling is limited (mitigated by the
`vscode_extension/`). For our integration, **policy authors are
specialists** (security engineers, compliance officers) for whom
learning Colang is a one-time cost; application developers don't
write rails, they reference policy IDs declared by the specialists.

Pip install + Colang config:

```bash
pip install nemoguardrails
```

Then a config directory:

```
my-rails/
├── config.yml          # models, rails enabled, kb path
├── prompts.yml         # task prompts
├── rails/
│   ├── input.co        # input rails (Colang)
│   ├── output.co       # output rails
│   └── dialog.co       # dialog rails
```

Then in Python:

```python
from nemoguardrails import LLMRails, RailsConfig
config = RailsConfig.from_path("./my-rails")
rails = LLMRails(config)
response = rails.generate(messages=[{"role": "user", "content": "..."}])
```

The DX is **policy-as-code** — rails live in `.co` files, version-controlled,
reviewable like any other code. The Colang grammar example from
`examples/configs/clavata_v2/main.co`:

```
import core
import llm

flow main
  activate llm continuation
```

Colang v2.x is materially richer (flow composition, type system). For
spring-ai-ascend, **Colang-as-policy is the most-portable contribution**
— our guardrail policies should live in source-controlled artifacts
under `docs/governance/guardrails/*.co` rather than as code-baked
runtime checks.

## 4. Multi-tenancy & governance

NeMo-Guardrails is **tenancy-agnostic** by design — it's a
request-pipeline filter, not a stateful service. There is no `tenant_id`
in the schema (verified by grep). Each `LLMRails` instance is
configured per-application; multi-tenant deployment means
instantiating one `LLMRails` per tenant (or per tenant+config combo)
in the host application. There is **no built-in audit log** of
rails-triggered events beyond OpenTelemetry traces, which means
**rails-rejection events do not persist** for forensic review unless
the host application emits a trace.

For our R-J + R-K compliance, we'd:

1. Instantiate `LLMRails` per `(tenantId, policyId)` in our
   `agent-middleware` layer.
2. Emit a `GuardrailEvent` on our `control` bus channel (Rule R-E)
   every time a rail rejects an input or output, capturing
   `(runId, tenantId, railName, decision, reason)`. This becomes
   durable audit evidence.
3. Wire OpenTelemetry traces (already supported by NeMo via
   `nemoguardrails/tracing/`) into our existing OTel pipeline.

## 5. Engine pluggability

NeMo-Guardrails has a clean **action-registry** pattern in
`nemoguardrails/actions/` plus the colang-driven dispatch. Actions are
plug-in points where Colang flows hand off to Python — registered via
`@action` decorator. The `library/` directory shows 27 third-party
integrations all sharing the same plug-in shape (each library
sub-package implements an `actions.py` and a `flows.co`). This is the
**most extensible safety taxonomy** in Tranche 4.

The `LLMRails.register_action(...)` + `register_action_param(...)` API
(visible in `llmrails.py`) is the integration surface for custom rails.
Adding a new rail means:

1. Write an `@action` decorated Python function.
2. Write a `.co` flow that invokes it.
3. Reference the flow in `config.yml` under
   `rails: { input: {flows: [...]} }`.

For our `EngineRegistry` (R-M.a), Colang flows would sit **at the
HookPoint layer** (R-M.c) — a `BEFORE_MODEL` HookPoint dispatches into
LLMRails for input validation; an `AFTER_MODEL` HookPoint dispatches
for output validation. Our `EngineEnvelope` carries a `policy_id` field
that selects which Colang config bundle applies.

## 6. Evolution substrate

NeMo-Guardrails does not maintain durable agent state, but it has two
substrate-adjacent contributions:

- **Knowledge base (`nemoguardrails/kb/`)**: RAG retrieval for
  factchecking. Each config can declare a kb path with documents that
  rails use to ground answers. The `factchecking` library
  (`nemoguardrails/library/factchecking/`) compares model output to kb
  passages and rejects unsupported claims.
- **Embedding cache (`nemoguardrails/embeddings/`)**: in-memory and
  persistent embedding caches for fast similarity matching during input
  rail evaluation (e.g. jailbreak-prompt similarity to known attacks).

The **evaluation framework** is the strongest evolution-adjacent
contribution: `nemoguardrails/eval/` + `nemoguardrails/evaluate/` +
`benchmark/` ship a full evaluation harness with metrics (precision,
recall, latency per rail). This is **the most-developed safety-eval
substrate in Tranche 4** and a direct prior for evaluating our own
guardrail effectiveness.

Additional evolution-adjacent contributions:

- **Cache layer** (`nemoguardrails/llm/cache/`): explicit cache for
  LLM calls during rail evaluation, with TTL + key-derivation
  strategies. Reduces evaluation cost when many requests trigger
  the same rail prompts.
- **Tracing v2** (`nemoguardrails/tracing/`): structured event
  emission compatible with OpenTelemetry. Each rail step emits a
  span; the full chain shows up in OTel UI as a tree.
- **Two Colang versions** (`v1_0` + `v2_x`): NeMo maintains both
  the original Colang and the v2.x revision. v2.x adds proper
  flow composition, type checking, multi-line action declarations.
  This **language-evolution discipline** is what we should mirror
  for our own DSLs (engine envelope schema, hook schema): version
  alongside, not replace.

The `qa/` directory at repo root contains adversarial testing
fixtures — prompts known to trigger jailbreaks, prompt-injection
attempts, PII-extraction attacks. Direct prior for our
`agent-evolve` plane's adversarial-evaluation corpus.

## 7. Deployment model

Two modes:

1. **Embedded library** — pip install, import `LLMRails`, run in-process.
   Default mode; OSS focus.
2. **Server** (`nemoguardrails/server/`): FastAPI server exposing rails
   via HTTP. Runs as a sidecar; the host application calls it for
   input/output validation.

`Dockerfile` exists at repo root for containerization. **No Helm chart**
in the repo (verified: no `Chart.yaml` at top level). Multi-arch
compatibility: Python 3.10-3.13 on `manylinux` wheels, no
NPU-specific code. Notable subdirectory: `vscode_extension/` ships
a VSCode extension for editing Colang files with syntax highlighting
— **the only Tranche-4 project with an IDE extension**. Five-plane
placement: **embedded mode** sits in `compute_control` (library inside
the engine); **server mode** sits in `bus_state` as a side-service.
For our integration, server mode is preferred — keeps the JVM agent
runtime isolated from the Python Colang runtime.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE-Apache-2.0.txt:1-3`; also
`LICENCES-3rd-party` for dependencies). Corporate sponsor: **NVIDIA
Corporation** (`pyproject.toml:4`, `authors = ["NVIDIA
<nemoguardrails@nvidia.com>"]`). The project is part of NVIDIA's NeMo
framework family. Permissive license, dependency-safe. The repository
moved from `NVIDIA/NeMo-Guardrails` to `NVIDIA-NeMo/Guardrails`
(`README.md:7-9`, badge URLs). Latest release **0.21.0** (2026-05-XX),
develop branch ahead at 0.22.0. The `develop` branch tracks top of tree
(`README.md:18`); we cloned `main`.

NVIDIA-sponsorship is **architecturally significant** — NeMo-Guardrails
is the safety surface most likely to ship with NVIDIA's enterprise AI
stack (NeMo Microservices, NIM, etc.), making integration valuable for
customers already on NVIDIA tooling. For our Ascend+Kunpeng positioning,
this is **not a hardware-coupling concern** — NeMo-Guardrails is pure
Python with no CUDA dependencies in the rail-dispatch path (CUDA only
appears in the optional embedding models).

## 9. What we LEARN

1. **Colang as policy-as-code DSL** — `.co` files in
   `nemoguardrails/rails/llm/llm_flows.co` (and the
   `examples/configs/*/rails/*.co`) define guard policies as
   version-controlled artifacts. We should ship guardrail policies
   under `docs/governance/guardrails/*.co` rather than baking them into
   Java code. This gives policy authors a clean editing surface
   independent of code reviewers.

2. **Library plug-in taxonomy** — `nemoguardrails/library/` ships 27
   safety integrations all sharing the same shape (each has
   `actions.py` + `flows.co`). Adopting this directory-per-rail pattern
   in our `agent-middleware/guardrails/` would give us the same
   extensibility story.

3. **Three-tier rail taxonomy (input / dialog / output)** —
   `rails: {input, dialog, output}` is the canonical decomposition.
   Our hook taxonomy (R-M.c) should map these to `BEFORE_MODEL`
   (input rails), `WITHIN_DIALOG` (dialog rails), `AFTER_MODEL` (output
   rails) HookPoints explicitly.

4. **Evaluation harness shipped first-class** — `eval/`, `evaluate/`,
   `benchmark/` together ship a metric-emitting evaluation framework.
   Direct prior for our `agent-evolve` plane's safety-eval surface —
   we should adopt the same precision/recall/latency-per-rail metrics
   for our guardrail audits.

5. **LangChain Runnable adapter pattern** —
   `nemoguardrails/integrations/langchain/runnable_rails.py:18-30`
   wraps `LLMRails` as a LangChain `Runnable`. For our LangChain4j
   counterpart story, this is the right reference shape.

6. **In-tree benchmarks** — `benchmark/` directory ships reproducible
   benchmarks. Combined with the evaluation harness, this is the
   strongest safety-empiricism story in the cohort.

7. **VSCode extension as developer surface** — `vscode_extension/`
   ships Colang syntax. We should consider an IntelliJ plugin / VSCode
   extension for our `module-metadata.yaml` and `docs/contracts/*.yaml`
   editing experience.

8. **Engine-registry naming + structure** —
   `nemoguardrails/guardrails/engine_registry.py` and
   `base_engine.py`. This is the **closest naming match** in Tranche 4
   to our `EngineRegistry` (Rule R-M.a). Examining its dispatch
   pattern (typed engine_type → concrete `BaseEngine` subclass)
   confirms that our R-M.a/.b contract is **convergent design**, not
   over-engineering — NVIDIA arrived at structurally similar shapes
   independently.

9. **Adversarial corpus discipline** — `qa/` directory ships
   adversarial prompts. Adopting an `agent-evolve/qa/` directory with
   the same shape (prompt + expected-rejection + category) gives our
   safety story a public-comparable benchmark.

10. **Colang dual-version coexistence** — `colang/v1_0/` +
    `colang/v2_x/` both present. The discipline of maintaining
    multiple language versions in one binary (rather than forcing
    flag-day migration) is the same discipline our envelope/hook
    contracts should follow as they evolve. Our
    `docs/contracts/*.v1.yaml` versioning is the prerequisite; the
    NeMo example shows it scales to multiple coexisting versions.

## 10. Integration surfaces (five-plane placement)

| Surface | What we adapt | spring-ai-ascend SPI | Five-plane home |
|---|---|---|---|
| **Input/output rail dispatch** | `LLMRails.generate()` per-request validation | `GuardrailSpi.validateInput(envelope)` / `validateOutput(response)` invoked at `BEFORE_MODEL` / `AFTER_MODEL` HookPoints (R-M.c) | `compute_control` |
| **Colang policy artifacts** | `.co` flow files in `rails/llm/` | `docs/governance/guardrails/<policy>.co` with policy_id → file mapping | `bus_state` config |
| **Library integration adapters** | `nemoguardrails/library/*` (27 third-party safety APIs) | `agent-middleware` ships subset as `GuardrailProviderSpi` implementations | `compute_control` |
| **Evaluation harness** | `eval/` + `evaluate/` metric framework | `agent-evolve` plane runs guardrail benchmarks against trajectory store | `evolution` |
| **OTel tracing** | `nemoguardrails/tracing/` OpenTelemetry sink | inject into our existing OTel pipeline | `bus_state` |
| **Server-mode HTTP API** | NeMo server `:8000` `/v1/chat/completions` with rails | `NemoGuardrailsAdapter` using Reactive `WebClient` (Rule R-G) | `compute_control` (caller) → `bus_state` (NeMo server) |
| **Guardrail event audit** | (gap — NeMo lacks audit) | emit `GuardrailEvent` on `control` channel for every rail decision | `compute_control` (emits) |

**Data shape we adapt**: NeMo's `RailsConfig` becomes a Java record
under `com.huawei.ascend.middleware.guardrails.spi.GuardrailPolicy`
carrying `String colangBundle`, `List<String> inputFlows`,
`List<String> outputFlows`. The `.co` files travel as opaque blobs
that the server-mode adapter loads.

**Operational pattern**: deploy NeMo-Guardrails in server mode as a
side-service in `bus_state` (one instance per cluster, multi-tenant via
config-bundle selection). Our `agent-execution-engine` calls it via
`HookPoint` middleware: at `BEFORE_MODEL`, send the prompt to
`/v1/validate_input`; at `AFTER_MODEL`, send the response to
`/v1/validate_output`. Non-OK responses transition the Run to FAILED
with reason `guardrail_rejected` and emit a `GuardrailEvent` on the
control channel. Audit logging is **our responsibility** since
NeMo-Guardrails ships no built-in audit beyond OTel traces.

**Where it sits**: `bus_state` (server instance) with a synchronous
caller bridge into `compute_control` (the engine's HookPoint
middleware). The Colang policy artifacts are version-controlled in
our repo under `docs/governance/guardrails/` and loaded into the NeMo
server at startup. NeMo-Guardrails is a load-bearing safety substrate
under PC-003; combined with Guardrails-AI (Tranche-4 cohort member),
they form the two halves of our safety story — NeMo for
dialog-flow-shaped policies, Guardrails-AI for structured-output
validation.

**Policy authoring workflow**: Colang policies live in
`docs/governance/guardrails/<policy-id>.co`. Authoring workflow:
write the `.co`, gate validates the syntax (we host a Colang parser
as a gate dependency), CI runs the policy against `qa/<policy-id>/`
adversarial corpus, reports precision/recall. Merge requires both
gate-green and benchmark-non-regression. This is **policy-as-code
under the same governance discipline as application code** — exactly
the model our Code-as-Everything principle (P-C) calls for. Policy
authors operate at the same review surface as engineers; no
out-of-band policy editor, no separate review process.

**Tenant-policy multiplexing**: in server mode, we deploy one
NeMo-Guardrails server per cluster, configured with multiple
`RailsConfig` bundles (one per tenant policy). Our adapter selects
the bundle by `tenantId` + `policyId` from the request envelope.
NeMo's startup cost is one-time per bundle, not per request, so
multi-tenant multiplexing on a single instance is feasible up to
~50-100 bundles per server. Beyond that, shard by `tenantId` hash
across NeMo server replicas. This is **operationally compatible**
with our Rule R-K capacity matrix — declare NeMo-server replicas
under `skill-capacity.yaml` with appropriate fanout.

**LangChain interoperability**:
`nemoguardrails/integrations/langchain/runnable_rails.py` wraps
`LLMRails` as a LangChain `Runnable`. For our LangChain4j-backed
engine adapter, we'd reproduce the same wrapper shape in Java,
exposing `Runnable<Input, Output>` over a `WebClient`-backed call
to our NeMo server. This gives application code targeting
LangChain4j idiomatic guardrail composition without coupling to
the underlying transport.
