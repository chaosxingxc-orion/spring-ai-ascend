# to-be flow delta — two-hop governance relay

Client → **[++] `GatewayRuntimeController`** (`POST /a2a`, the S4-deferred HTTP entry) → **[~] `GatewayRuntimeService.dispatchRequest`** enqueues the gateway outbox and produces hop1 (`T_invocation_req`).

**[++] `EventBusRelayWorker`** polls hop1 and applies **GOVERNANCE between hops** — inbox dedup (`ON CONFLICT DO NOTHING` + `SKIP LOCKED` claim), tenant check, correlation match, audit (the L2 §1.2/§4.1 semantics, non-byte-transparent) — then re-publishes hop2: outbox enqueue → `relay.produce` → `T_invocation_deliver` (provisioned-unused in as-is, now wired).

**[++] `TestAgentRuntime`** (in-repo test-double) or the external agent-runtime consumes hop2, serves, and produces the response on `T_invocation_resp_in`. **[++] symmetric relay** polls resp_in → governance (dedup/corr/audit) → re-publish `T_invocation_resp_out`. **[~] `acceptWindow`** polls resp_out → classify → `IngressResponse`.

**Ties to decision tree:** Q1a (relay does consume→govern→re-produce reusing inbox/outbox/relay/consumer ports), Q4a (in-repo double for hop2). The boxed governance step is the as-is zero-coverage gap — now the exercised core of the flow.
