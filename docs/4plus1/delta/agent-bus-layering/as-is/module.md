# as-is · module — agent-bus layering

**Uniform snapshot (before the change):** every node is a coequal present-state element. Module type is conveyed ONLY by the `[SRV]`/`[PORT]`/`[LIB]`/`[DB]`/`[CFG]` text tag + the dashed package group — never by fill or border color (L7). No delta vocabulary (added/modified/removed) applies here.

## What the diagram shows

`com.huawei.ascend.bus..` (one `AgentBusApplication`, default component-scan covers every sub-package) currently organizes into:

- **`gateway.runtime`** — the **mixed** package. 7 files: 3 gateway-specific (`GatewayRuntimeConfiguration` `@Profile(gateway)`, `GatewayRuntimeController` `@RestController`, `GatewayRuntimeService`) + 4 event-bus wiring (`EventBusRelayConfiguration` `@Profile(eventbus)`, `EventBusRelaySchedulingConfig` `@Profile(eventbus)`, `RelayScheduler`, `AgentBusBrokerProperties`). The confusion point: "Spring wiring落点" ≠ "gateway 独有代码" — the package conflates two planes + the shared config.
- **`forwarding.spi`** `[PORT]` — `ForwardingInboxPort`/`ForwardingOutboxPort`/`ForwardingRouteHandle`. The external SPI candidate (gateway should depend on here, not on event-bus implementation).
- **`forwarding.runtime.relay`** `[LIB]` — `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop`. Pure-Java domain (ArchUnit-guarded, untouched).
- **`forwarding.runtime.transport.broker`** `[PORT]` — `BrokerClientProperties` + RocketMQ adapters (concrete broker client confined here by purity rule).
- **`forwarding.runtime.persistence.jdbc`** `[DB]` — `JdbcForwardingInbox`/`JdbcForwardingOutbox` (Spring/JDBC confined here by purity rule).
- **`registry.runtime`** `[SRV]` — registry plane wiring (unchanged by this refactor).

## The two load-bearing dependency facts (drive G3-Q2 + the ripple)

1. **`AgentBusBrokerProperties` is SHARED** — `EBRCFG`/`EBSCHED` (event-bus) AND `GWCFG` (gateway) both depend on it; both annotate `@EnableConfigurationProperties(AgentBusBrokerProperties.class)`. It is `@ConfigurationProperties("agent-bus")` (plane-level config, 12 fields: broker endpoints + per-form service ids + timing knobs + tenant). Moving it into `eventbus.runtime` would make **gateway depend on the event-bus implementation package** for its config — inverted vs the intended "gateway depends on event-bus SPI, not impl" layering. → ownership is G3-Q2 (recommended: a neutral `common` package).
2. **`GatewayRuntimeConfiguration` ↔ `AgentBusBrokerProperties`** are same-package today (no `import` needed). After `AgentBusBrokerProperties` moves, `GatewayRuntimeConfiguration` needs an explicit `import` — the one real gateway-side ripple.

## ArchUnit: nothing here constrains gateway/eventbus (the corrected finding)

No ArchUnit rule scopes `gateway..` or `eventbus..`. The 4 purity tests guard `forwarding..`/`spi..`/`spi.registry..`/`registry..`; the boundary test guards `bus..`-vs-sibling-modules; the design-contract test trip-wire targets `broker/queue/mailbox/dlq/replay` package-name fragments. `eventbus.runtime` matches none. **Moving the 4 files breaks zero rules; grants no licenses.** (Full audit in `scope.md` "CRITICAL as-is finding".)
