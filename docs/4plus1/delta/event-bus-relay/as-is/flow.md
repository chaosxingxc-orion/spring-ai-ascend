# as-is flow view — request/response control flow

A client call enters `GatewayRuntimeService.routeClientRequest` → `dispatchRequest` does **synchronous inline** enqueue → `claimDue(1)` → `relay.produce` → `markAcked` (the `:170` comment flags "S4 adds the worker loop"), then `acceptWindow` synchronously polls the response consumer, committing self-own/cross-tenant/non-matching messages and classifying a correlationId-matching response by its native `eventType` into an `IngressResponse`.

The request lands on `T_invocation_req`, but the **only consumer** of that topic is `TestAgentRuntime` in tests — there is no event-bus process consuming it, applying inbox dedup / tenant check / correlation match / audit, and re-publishing to `T_invocation_deliver`. That deliver topic is provisioned by `rocketmq-init` but **never used**. Responses come back on `T_invocation_resp_out` and are polled by the gateway's accept window.

So the as-is is a **single synchronous hop** with the governance-between-hops that L2 §1.2/§4.1 mandates **entirely absent and untested**. The gateway's `acceptWindow` does client-side tenant/correlation filtering (the deferred-#3 D13 drift mode), not the broker-side governance relay.
