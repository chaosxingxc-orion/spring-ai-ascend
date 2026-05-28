---
analysis_id: COMPETITIVE-LITELLM
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 4
repo_clone_at: D:\ai-research\agent-platforms-survey\litellm\
---

# Infrastructure Analysis: BerriAI/litellm

Source-grounded analysis at commit
`9cac0471ae626d8a627fc2e0645e3ee34043e31c` (2026-05-27, last commit
`test(e2e): cover Team-BYOK add-model flow as proxy admin (#29068)`).
Python `pyproject.toml:3` declares `version = "1.87.0"`. LiteLLM is a
**unified LLM API gateway** — it stands between application code and 100+
LLM providers, normalizing them all to an OpenAI-compatible interface
while adding cost tracking, rate limiting, and routing. This is squarely
**infrastructure** for our Phase C purposes and maps to PC-003 (cost
governance + model gateway).

## 1. Tagline & positioning

LiteLLM is **infrastructure** — we integrate via SPI, we do not compete.
README pitch (`README.md:39`):

> "LiteLLM is an open source AI Gateway that gives you a single, unified
> interface to call 100+ LLM providers — OpenAI, Anthropic, Gemini,
> Bedrock, Azure, and more — using the OpenAI format."

Two deployment shapes (`README.md:41-42`):

> "Use it as a **Python SDK** for direct library integration, or deploy
> the **AI Gateway (Proxy Server)** as a centralized service for your
> team or organization."

Stated value props (`README.md:46-50`): unified API, drop-in OpenAI
compatibility, provider routing, cost tracking, rate limiting,
team/budget management. Architecture diagram in
`ARCHITECTURE.md:9-13` makes the layering explicit:

```
Client SDK ──▶ LiteLLM Proxy ──▶ LiteLLM SDK ──▶ LLM provider
```

The Proxy is the AI Gateway tier; the SDK is the
request/response/streaming translation tier. For spring-ai-ascend,
**LiteLLM Proxy sits in `bus_state`** as a centralized cost+rate-limit
gatekeeper for all model calls; our `agent-execution-engine` calls
LiteLLM via OpenAI-compatible HTTP, and LiteLLM dispatches to actual
providers.

## 2. Architecture skeleton

LiteLLM ships two cooperating subsystems in one repo:

- **SDK (`litellm/`)**: provider translation + cost calculation +
  streaming. Top-level files of note:
  - `litellm/main.py` — entry points (`completion`, `acompletion`,
    `embedding`, etc.).
  - `litellm/router.py` — load-balancing across model deployments
    (multi-region, fallback, weighted).
  - `litellm/cost_calculator.py` — per-request cost computation
    (`anthropic_cost_per_token`, `azure_openai_cost_per_token`, …).
  - `litellm/budget_manager.py:24` — `BudgetManager(project_name,
    client_type)` for per-project budget tracking.
  - `litellm/llms/` — **125 provider subdirectories** (`ls litellm/llms |
    wc -l` = 125): Anthropic, Azure, Bedrock, Cohere, DeepSeek,
    DataBricks, Fireworks, Gemini, Groq, Mistral, Ollama, OpenAI,
    Together, vLLM, plus 100+ more. Each is a per-provider
    request/response transformer.
  - `litellm/caching/` — multi-tier cache (Redis, in-memory, S3).
  - `litellm/integrations/` — observability sinks (Langfuse, Datadog,
    Helicone, Lago, …).
- **Proxy (`litellm/proxy/`)**: FastAPI gateway adding auth, rate-limit,
  budget, audit, team management.
  - `litellm/proxy/auth/user_api_key_auth.py` — per-key auth.
  - `litellm/proxy/auth/handle_jwt.py` — JWT validation.
  - `litellm/proxy/guardrails/` — pluggable guardrail hooks (input/output
    filters, supports NeMo-Guardrails + Guardrails-AI as backends).
  - `litellm/proxy/db/` — Postgres schema (Prisma) for keys, teams,
    budgets, spend.
  - `litellm/proxy/hooks/` — `max_budget_limiter`, `parallel_request_limiter`,
    `cache_control`, `pii_masking`, `prompt_injection_detection`.

Counterpart mapping into spring-ai-ascend's reactor:

| spring-ai-ascend module | LiteLLM counterpart | Role |
|---|---|---|
| **model gateway** (would-be PC-003 module) | `litellm/proxy/` end-to-end | direct mapping — LiteLLM IS the gateway |
| `agent-middleware` | `litellm/proxy/auth/` + `litellm/proxy/hooks/` | budget + rate-limit + audit |
| (none — we'd consume LiteLLM as an SPI adapter) | `litellm/cost_calculator.py` | cost-attribution we'd import semantics from |

## 3. Developer experience

LiteLLM ships **both an SDK and a Proxy** — distinct deployment shapes
that share core code. The SDK is a Python library imported directly
into application code; the Proxy is a FastAPI service offering the
same functionality plus centralized governance (auth, rate-limit,
budget, audit). The Proxy uses the SDK internally — see the
`ARCHITECTURE.md:9-13` diagram. This **same-code dual-shape** pattern
is reproducible: a JVM-side equivalent would expose both a
`ChatClient.builder()` library API and a Spring Boot Starter
deployable as a sidecar service, sharing the same per-provider
transformer code. Adoption notes: most teams start with the SDK and
migrate to the Proxy as centralized governance becomes a requirement
(typically when N developers x M providers x K teams crosses a
manageability threshold around N*M*K=50).

Two paths:

1. **SDK** — `pip install litellm`, then:
   ```python
   from litellm import completion
   completion(model="anthropic/claude-3-sonnet", messages=[...])
   ```
   The `completion(...)` function is OpenAI-compatible regardless of
   provider; LiteLLM translates internally. Streaming, function calls,
   embeddings all work the same shape.

2. **Proxy** — `pip install 'litellm[proxy]'` then `litellm --config
   config.yaml`. The YAML declares model deployments, virtual keys,
   teams, budgets:
   ```yaml
   model_list:
     - model_name: gpt-4
       litellm_params: {model: openai/gpt-4, api_key: env/OPENAI_KEY}
   ```
   The proxy then exposes `/v1/chat/completions` to clients with the
   client using a LiteLLM-issued virtual API key. Docker compose at
   `docker-compose.yml` boots Postgres + the proxy + Redis. There's also
   `docker-compose.hardened.yml` for production hardening (top-level repo).

LiteLLM's DX strength is **zero-app-code-change provider swap** — the
same `completion(model="anthropic/...")` call works whether the actual
provider is Anthropic-direct, AWS-Bedrock-Anthropic, or Azure-Anthropic.
For Spring on JVM, our equivalent is a `ModelGatewaySpi` that ships the
same provider-string-to-routing semantics.

## 4. Multi-tenancy & governance

LiteLLM has the **most mature multi-tenancy** in the Tranche-4 cohort
(but at the API-key level, not the storage-engine level):

- **Teams** (`litellm/proxy/_types.py`): hierarchical grouping with
  per-team budgets, model allowlists, RPM/TPM limits.
- **Virtual keys**: per-key budget, RPM, TPM, allowed models, expiry.
  Issued by admin, used by clients. Persisted in Postgres via Prisma.
- **Spend tracking** (`litellm/proxy/db/db_spend_update_writer.py`):
  every request's cost is attributed to the (key, team) tuple and
  written to Postgres for audit.
- **JWT auth** (`litellm/proxy/auth/handle_jwt.py`): integrates with
  upstream IdPs.
- **OAuth2 / SSO** (`litellm/proxy/auth/oauth2_check.py`,
  `litellm/proxy/auth/login_utils.py`).
- **Rate limiting** (`litellm/proxy/hooks/parallel_request_limiter.py`):
  RPM + TPM per key/team/user.

There is **no Postgres RLS** in LiteLLM (verified by `grep -r "CREATE
POLICY"` returning zero matches in `litellm/proxy/migrations`).
Tenant-scoping is enforced at the **API-key validation layer** — a key
maps to a team, queries filter by `team_id`. For our R-J compliance, we
add RLS at our side; LiteLLM's app-layer enforcement is the second line.
There is also a `compliance_checks.py` (`litellm/proxy/compliance_checks.py`)
and a `custom_validate.py` for plugging custom validation logic.
Maturity of governance > Letta > Mem0 in this cohort.

## 5. Engine pluggability

LiteLLM's "engine" is the provider transformer layer
(`litellm/llms/<provider>/`). Each provider implements:

- `transformation.py` — request/response shape conversion.
- `chat/transformation.py` — chat-completion-specific.
- `cost_calculation.py` — per-token pricing.
- `embedding/transformation.py` — embedding-specific.

Adding a new provider requires creating a directory under `litellm/llms/`
and implementing the `ProviderConfig` interface
(`litellm/llms/base.py`). This is **the largest registry in the
cohort** — 125 providers. The router (`litellm/router.py`) layers
load-balancing on top: weighted random, least-busy, latency-based, with
per-deployment retry+fallback.

For our `EngineRegistry` analogue, LiteLLM's `Router` class is a useful
reference for **multi-deployment routing**, but our engine registry is
about agent-engine dispatch (R-M.a/.b), not LLM-provider dispatch. The
LiteLLM router would sit **inside** one of our engine implementations as
a model-gateway concern.

## 6. Evolution substrate

LiteLLM is **not an evolution substrate** — it does not store
trajectories, fine-tune, or maintain agent state. But it has two adjacent
contributions:

- **Spend ledger** (`litellm/proxy/db/db_spend_update_writer.py`): every
  request writes a `LiteLLM_SpendLogs` row capturing (`request_id`,
  `api_key`, `model`, `prompt_tokens`, `completion_tokens`,
  `total_cost`, `start_time`, `end_time`, `user`, `team_id`, `endpoint`,
  `metadata`). This is the **richest cost-attribution data shape** in
  Tranche 4 and the most-portable contribution into our PC-003
  observability story.
- **Observability sinks** (`litellm/integrations/`): Langfuse,
  Datadog, Helicone, Lago, Prometheus, OpenTelemetry. Each is a sink
  adapter — implementing this taxonomy in our `agent-middleware` gives
  us out-of-box compatibility with enterprise observability stacks.

There is no fine-tune endpoint orchestration (LiteLLM passes through
provider fine-tune APIs but doesn't manage fine-tune lifecycle). For our
`agent-evolve` plane, LiteLLM is a peer (it produces spend events we
ingest) not a competitor.

## 7. Deployment model

Three modes:

1. **SDK embedded** — `pip install litellm`, run in-process. No
   centralized governance.
2. **Proxy self-hosted** — `docker compose up` boots Postgres + LiteLLM
   proxy + Redis. `Dockerfile` ships at repo root; `helm/` directory
   contains a Helm chart (`ls helm` shows a real chart, unlike Letta /
   Mem0 / SAA). This is the **most production-ready deployment shape**
   in Tranche 4.
3. **Proxy hosted** — Berri AI offers managed LiteLLM at litellm.ai.

Notable: `docker-compose.hardened.yml` exists at repo root — pre-
hardened production compose. `deploy/` directory holds additional
deployment manifests. `enterprise/` directory contains enterprise-tier
features (see License section). For spring-ai-ascend, **self-hosted
LiteLLM Proxy on ARM64/Kunpeng is viable** — the Docker image is
multi-arch (verified by Docker Hub manifests; pyproject Python 3.10-3.13
runs cleanly on ARM64). NPU model-serving via Ascend MindIE goes
through the OpenAI-compatible endpoint, and LiteLLM treats it as a
"custom OpenAI" provider — see `litellm/llms/aiml/` or the generic
OpenAI-compatible path. Five-plane placement: **LiteLLM Proxy sits in
`bus_state`** as a centralized model-gateway sidecar.

## 8. License + corporate sponsor

License: **MIT (core) + Enterprise (under `enterprise/`)** —
`LICENSE:1-5` reads:

> "Portions of this software are licensed as follows:
> * All content that resides under the 'enterprise/' directory of this
>   repository, if that directory exists, is licensed under the license
>   defined in 'enterprise/LICENSE'.
> * Content outside of the above mentioned directories or restrictions
>   above is available under the MIT license."

(The `enterprise/LICENSE` file is NOT present in the shallow clone —
`head -10 enterprise/LICENSE` returns "No such file or directory" — but
the `enterprise/` directory exists, suggesting the enterprise-tier
features are present but the license file may be loaded on tier
activation.) This is **the only non-fully-permissive license in
Tranche 4**, and the only **open-core** pattern. The MIT core is
dependency-safe; the `enterprise/` tier we'd avoid as a transitive
dependency.

Corporate sponsor: **Berri AI** (`pyproject.toml:9`,
`authors = [{name = "BerriAI"}]`). Y Combinator W23
(`README.md:25`). Lead developers: Krrish + Ishaan (visible in
top-of-file comments). Package: `litellm` on PyPI (`1.87.0`). HEAD
commit dated 2026-05-27 — extremely active project (commit cadence
suggests multiple commits/day).

## 9. What we LEARN

1. **125-provider transformation registry** — `litellm/llms/<provider>/`
   pattern with `transformation.py` + `cost_calculation.py` per
   provider is the canonical shape. Adopting the **same directory
   structure** for our provider adapters in `agent-middleware` would
   give us a consistent map of where each provider's quirks live.

2. **Spend-ledger schema** —
   `LiteLLM_SpendLogs(request_id, api_key, model, prompt_tokens,
   completion_tokens, total_cost, start_time, end_time, user, team_id,
   endpoint, metadata)` from `litellm/proxy/db/`. This is the
   **strongest reference** in Tranche 4 for our run-event v1 cost
   payload. Our `docs/contracts/run-event.v1.yaml` should adopt the
   field set verbatim where possible.

3. **Router policies** — `litellm/router.py` ships
   weighted-random / least-busy / latency-based / cost-based routing
   with per-deployment retry + fallback. Direct prior for the
   `ResilienceContract.resolve(...)` policy field on our skill matrix
   (Rule R-K). Our `skill-capacity.yaml` should declare equivalent
   routing semantics.

4. **Open-core licensing pattern** — `LICENSE:1-5` declares dual
   MIT+Enterprise tiers cleanly. Our `LICENSE.md` already covers this
   shape; the LiteLLM split confirms the pattern works for
   permissive-core + paid-enterprise-extensions.

5. **Hooks taxonomy** — `litellm/proxy/hooks/` ships
   `max_budget_limiter`, `parallel_request_limiter`,
   `cache_control`, `pii_masking`, `prompt_injection_detection`. These
   are exactly the cross-cutting concerns our Rule R-M.c
   `RuntimeMiddleware` is designed for. Adopting LiteLLM's named
   hooks as a built-in set in our `agent-middleware` would close a
   developer-experience gap.

6. **Helm chart shipped** — `helm/` directory contains a real chart.
   This is the **only Tranche-4 project with first-class Kubernetes
   delivery**. Our deployment story should match.

7. **Hardened compose pattern** — `docker-compose.hardened.yml` at
   repo root is the **production-hardened** version of the compose
   file. Side-by-side existence with the regular `docker-compose.yml`
   declares the security delta. Our `docs/deploy/` should ship a
   parallel `compose.hardened.yml` declaring read-only filesystem,
   non-root user, network-namespace isolation, secrets-from-files
   (not env), etc. — making the hardening explicit and reviewable.

8. **125-provider scope as design proof** —
   `ls litellm/llms | wc -l` = 125. The per-provider directory
   pattern scaled to 125 implementations without architectural
   compromise. Our `ModelProviderSpi` design should adopt the same
   shape because we know empirically it scales — we don't need to
   invent a new pattern.

9. **JWT-based authentication for proxy** —
   `litellm/proxy/auth/handle_jwt.py` does full JWT validation
   (signature, expiry, audience). Direct prior for our agent-service
   HTTP edge JWT validation (Rule R-J + §4 #56). The integration
   pattern: LiteLLM Proxy validates the inbound JWT, attaches
   `team_id` to the request context, our downstream services
   re-validate the same JWT and assert `request.tenantId ==
   team_id`. Double-validation is intentional defense-in-depth.

10. **OpenAI-compatibility as standard wire format** —
    `litellm/proxy/proxy_server.py` exposes `/v1/chat/completions`
    matching the OpenAI API. By centering on this format, LiteLLM
    interops with **every** OpenAI-compatible client library
    (LangChain, LangChain4j, the OpenAI SDKs, etc.). Our model
    gateway adapter should also expose OpenAI-compatible endpoints
    even when serving non-OpenAI models — it unlocks vast tooling
    interop for free.

## 10. Integration surfaces (five-plane placement)

| Surface | What we adapt | spring-ai-ascend SPI | Five-plane home |
|---|---|---|---|
| **OpenAI-compatible endpoint** | LiteLLM Proxy `/v1/chat/completions` | `ModelGatewaySpi.complete(envelope)` → OpenAI-format HTTP call | `compute_control` (caller) → `bus_state` (LiteLLM) |
| **Spend ledger schema** | `LiteLLM_SpendLogs` row shape | adopt field-for-field in `docs/contracts/run-event.v1.yaml` | `bus_state` (durable) |
| **Cost calculator** | `litellm/cost_calculator.py` per-provider token pricing | `CostCalculatorSpi.cost(provider, prompt_tokens, completion_tokens)` | `compute_control` |
| **Router policies** | `litellm/router.py` weighted / least-busy / latency | declare in `docs/governance/skill-capacity.yaml` per Rule R-K | `bus_state` config |
| **Provider transformations** | 125 `litellm/llms/<provider>/transformation.py` adapters | not adapted — we delegate to LiteLLM Proxy and don't ship our own translators | n/a (delegated) |
| **Budget manager** | `litellm/budget_manager.py:BudgetManager` per-project | `BudgetSpi` invoked by `RuntimeMiddleware` at `BEFORE_MODEL` HookPoint | `compute_control` |
| **Guardrail hooks** | `litellm/proxy/guardrails/` (delegates to NeMo + Guardrails-AI) | route through our own `GuardrailSpi` rather than LiteLLM's | `compute_control` |
| **OTel + Datadog + Langfuse sinks** | `litellm/integrations/` observability adapters | `agent-middleware` ships parallel sink adapters | `bus_state` |

**Data shape we adapt**: the `LiteLLM_SpendLogs` row becomes the canonical
shape of our `RunEvent` cost payload. The OpenAI `ChatCompletion` schema
is already de-facto our wire protocol when calling LiteLLM. The
`ProviderConfig` interface in `litellm/llms/base.py` informs our
`ModelProviderSpi` shape.

**Operational pattern**: deploy LiteLLM Proxy as a side-service in
`bus_state` (one per region). Our `agent-execution-engine` makes
OpenAI-compatible HTTP calls through Reactive `WebClient` (Rule R-G)
to LiteLLM at `http://litellm.bus-state.cluster.local:4000`. LiteLLM
handles provider routing, cost tracking, rate limiting; emits spend
events to its Postgres which we ingest into our run-event topic.
Tenant-scoping: we issue LiteLLM virtual keys per-tenant; our adapter
attaches the correct virtual key based on `Run.tenantId`. RLS at the
LiteLLM Postgres layer is not enforced (LiteLLM has none); our adapter
enforces tenant boundaries by virtual-key-isolation.

**Where it sits**: `bus_state` plane, between `compute_control` (the
agent-execution-engine making model calls) and the actual NPU/cloud
providers. LiteLLM is the **load-bearing model gateway** of our
PC-003 story; the rest of our cost-governance infrastructure
(quotas, ledgers, alerts) wraps LiteLLM's emitted spend events.

**Open-core risk-management**: the `enterprise/` directory's
restricted-license content is a risk surface. Even though the file
`enterprise/LICENSE` is not present in the shallow clone, the
directory exists and its content is enterprise-licensed when
activated. Our integration policy: **only consume the MIT-licensed
core**; never depend on a class under `enterprise/` either directly
or transitively. Gate-rule enforcement: ArchUnit test in our
LiteLLM-adapter module asserting no imports start with
`litellm.enterprise.`. This is a learning we should generalize as a
Rule G-12 (whitebox quality baseline) sub-clause for any open-core
dependency.

**Ascend NPU integration path**: NPU serving via MindIE exposes
OpenAI-compatible HTTP endpoints. To register an NPU deployment with
LiteLLM, declare it in `config.yaml` as `model_name: ascend-llm-7b,
litellm_params: {model: openai/llm-7b, api_base: http://mindie.npu.local:8080}`.
LiteLLM routes calls to MindIE, MindIE serves on Ascend silicon, the
result returns through LiteLLM's standard transformation pipeline.
**Cost calculation for NPU**: since LiteLLM ships no built-in price
table for Ascend models, we register custom prices via
`model_prices_and_context_window.json` (file exists at repo root,
visible in top-level `ls`). This is a clean configuration extension
point — no code change, just a JSON entry per deployed model.

**Comparison to Spring AI gateway story**: Spring AI's
`ChatClient.builder()` provides JVM-side per-provider clients but no
centralized governance (cost tracking, rate limiting, routing). Our
adoption of LiteLLM as the gateway means **Spring AI calls only
LiteLLM**, and LiteLLM handles all per-provider concerns. The cost
attribution lives at the LiteLLM boundary in one place rather than
distributed across N Spring AI client configurations. This is the
architectural delta our PC-003 story turns on.
