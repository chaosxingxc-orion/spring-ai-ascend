# to-be module delta — event-bus two-hop relay

The single agent-bus codebase launches in **two process forms** via Spring profiles (Q2a). Every node colored against as-is: `++` green added · `~` amber modified · `--` red legacy/removed.

- **[++] GATEWAY process** — `AgentBusApplication @Profile(gateway)` + new `GatewayRuntimeConfiguration` wires `GatewayRuntimeService` **as a bean** (`~`, was unwired), the S4-deferred `GatewayRuntimeController` (`POST /a2a`, `++`), and the gateway response consumer (`++`, polls `T_invocation_resp_out`).
- **[++] EVENT-BUS+REGISTRY process** — `AgentBusApplication @Profile(eventbus)` + new `EventBusRelayConfiguration` wires the **core net-new** `EventBusRelayWorker`/`RelayLoop` (consume→govern→re-produce, `++`), the JDBC inbox/outbox (`~`, now wired for dedup/governance), and the registry plane (`·`, co-located, unchanged). The relay **reuses all substrate ports** (`ForwardingInboxPort`/`ForwardingOutboxPort`/`BrokerForwardingRelayPort`/`BrokerForwardingConsumerPort` + state machine/retry/breaker) per Q1a — no new ports.
- **[~] RocketMQ** — all 8 topics now USED; `T_invocation_deliver` / `T_invocation_resp_in` / `T_invocation_resp_out` (provisioned-unused in as-is) are wired.
- **[--] `A2aForwardingDeliveryPort`** — T1 HTTP push, legacy; replaced by broker pub/sub for FEAT-013/014 scope (retained for coexistence per constraint #4; T1→T4 switch deferred Stage 30).
- **[++] deployment** — `spring-boot-maven-plugin <repackage>` on agent-bus (fat-jar; `~` `AgentBusApplication`'s `java -jar` claim now fulfilled) + docker-compose `gateway` & `event-bus` services (Q3a).

**Ties to decision tree:** Q1a (new relay from existing ports), Q2a (profiles), Q3a (fat-jar + compose). The gateway/response-consumer and controller deltas land the S4 items L2 §8.3 flags as deferred.
