# as-built · module diff — forwarding-reorg (G6)

Diff of the as-built (post-G5 real code) vs the to-be plan (`to-be/module-delta.md`). The plan **landed as designed** with **4 Drifted ⚠** (1 user-accepted decision change + 3 mechanical/favorable), **0 Missing**, **0 Surprise**.

## Matched ✓ (as-built realised the to-be delta)

- ✓ **`++ forwarding/common/`** — `AgentBusBrokerProperties` moved (top-level `common/` eliminated).
- ✓ **`++ forwarding/spi/broker/`** — the 5 broker SPI classes (2 Port interfaces + 3 value-object records) moved; code-verified they reference only `forwarding.spi` (no inversion).
- ✓ **`++ forwarding/runtime/relay/`** — the 3 event-bus wiring files folded in (top-level `eventbus.runtime/` eliminated); co-located with `EventBusRelayWorker` (Q2).
- ✓ **`~ forwarding/runtime/transport/broker/`** — 4 common classes stay; the 5 SPI + 2 rocketmq extracted out.
- ✓ **`++ forwarding/runtime/transport/broker/rocketmq/`** — the 2 rocketmq impl moved (sibling dir for future brokers).
- ✓ **`~ gateway/runtime/`** — `GatewayRuntimeConfiguration` import → `forwarding.common`.
- ✓ **Build (Q1=b)** — single module + repackage + `@Profile` UNCHANGED (no pom split).
- ✓ **2 top-level pkgs eliminated** — `common/` + `eventbus.runtime/` folded into `forwarding`.

## Drifted ⚠ (as-built differs from to-be — all accepted)

- ⚠ **Rule scope narrowed: `gateway↛forwarding.runtime` → `gateway↛forwarding.runtime.relay`** (user-accepted 2026-07-16). The to-be's `↛forwarding.runtime..` was **infeasible** — the to-be plan contradicted itself (the dependency-direction section had the gateway wiring `forwarding.runtime.{broker, jdbc, transport}` as a producer/consumer, which the rule would forbid). The as-built enforces the **real** plane boundary: the gateway (producer/consumer) never reaches into the **event-bus relay** mechanism/wiring (`forwarding.runtime.relay`), but legitimately wires the broker adapters + jdbc outbox + transport (SDK surface). Recorded in `deviations.md` (b); ADR-0163 notes it; a future stricter rule (gateway↛broker/jdbc/transport via a forwarding auto-config) is flagged as a follow-on.
- ⚠ **4 ArchUnit tests evolved, not 2.** The to-be named 2; the reorg folded Spring/RocketMQ/`javax.sql`-using wiring (`EventBusRelayConfiguration` etc. + `AgentBusBrokerProperties`) INTO `forwarding..` (from the exempt `eventbus.runtime`/`common` planes), bringing them under the `forwarding..` purity rules. Fixes mirror the existing `persistence.jdbc`/`transport.broker` exemption pattern: `AgentBusForwardingSpiPurityTest` exempts `forwarding.runtime.relay` + `forwarding.common`; `AgentBusForwardingRuntimeContractTest` + `AgentBusForwardingDesignContractTest` add `forwarding.spi.broker` to path-exclusions/sanctioned-homes. Recorded in `deviations.md` (b).
- ⚠ **5 tests MOVED (not import-only) — build-forced by package-private access.** The 4 rocketmq tests call package-private seams (`sql92Expression`/`buildMessage`/`drainAll`) of the moved impl → moved to `broker/rocketmq/`; `EventBusRelaySchedulerTest` mirrors `RelayScheduler` → moved to `forwarding/runtime/relay/`. 5 broker tests stayed import-only (public API only). Recorded in `deviations.md` (a).
- ⚠ **`RocketMqBrokerForwardingConsumer`/`Relay` reference NO broker-common types** (favorable — less work than the to-be anticipated). The to-be said "possibly — CHECK"; code-verified: SPI-only imports (`forwarding.spi.broker.*`), no parent-`broker/` imports needed. Recorded in `deviations.md` (b).

## Missing ✗ (to-be said X, as-built has no X)

- ✗ none.

## Surprise ⚠⚠ (as-built has Y, to-be never mentioned Y)

- ⚠⚠ none. The rule-scope infeasibility was caught + the user accepted the narrower rule (not a surprise — a correction).

## Suite verification (DoD)

- `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (BUILD SUCCESS; ArchUnit test count net 0 — 2 boundary tests replaced by 2). The 14 skipped = the 3 `RealBroker*` IT (env-guarded).
- All 4 evolved ArchUnit tests green (incl. the `gateway↛forwarding.runtime.relay` rule + liveness guard + the purity exemptions).

## Net

All Matched ✓ + 4 Drifted ⚠ (1 user-accepted decision change — rule scope; 3 mechanical/favorable — arch-test count, test moves, rocketmq-no-common) + 0 Missing + 0 Surprise. The forwarding-centric reorg landed; the build is single-module+@Profile (multi-module deferred); the gateway/event-bus plane boundary is enforced at the relay-package granularity (the realistic boundary).
