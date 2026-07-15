# as-is sequence view — slice-3 IT (only end-to-end exercise of the broker substrate)

The only end-to-end path that drives the real broker substrate is the slice-3 IT. Client → `GatewayRuntimeService.dispatchRequest` (synchronous enqueue/claim/produce/markAcked) → `RocketMqBrokerForwardingRelay` → broker `T_invocation_req` → `TestAgentRuntime` polls it **directly** (LitePull adapter) with no relay in between → `TestAgentRuntime` produces `T_invocation_resp_out` → the gateway's `RocketMqBrokerForwardingConsumer` polls → `classify` → `IngressResponse`.

`T_invocation_deliver` is provisioned by `rocketmq-init` but never used. The governance acts (inbox dedup, tenant check, correlation match, audit) that L2 §1.2/§4.1 places **between the two hops** are entirely absent from the as-is sequence and have zero test coverage. This is the single-hop shape the to-be two-hop relay (gateway→broker→event-bus governance→broker→runtime, symmetric response) must replace.
