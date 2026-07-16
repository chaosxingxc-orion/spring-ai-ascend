# CLOSED — forwarding-reorg (arch-driven)

- **Change slug:** `forwarding-reorg`
- **Closed:** 2026-07-16
- **ADR:** [0163](../../../../adr/0163-forwarding-reorg.md) (new; **supersedes ADR-0162**)
- **Delta artifacts:** `docs/4plus1/delta/forwarding-reorg/` (as-is · decision-tree · to-be+sign-off · deviations · as-built+diff · overview.html · this CLOSED.md)

## What the change was

Reorganize `agent-bus` into a **forwarding-centric** structure that frames separate package boundaries for the event-bus process form / producer-consumer SDK / gateway process form: fold the 3 event-bus wiring files into `forwarding/runtime/relay/` (eliminating top-level `eventbus.runtime/`); move `AgentBusBrokerProperties` to `forwarding/common/` (eliminating top-level `common/`); extract the broker SPI (2 Port interfaces + 3 value-object records) to a NEW `forwarding/spi/broker/`; move the 2 rocketmq impl to a NEW `forwarding/runtime/transport/broker/rocketmq/`; keep the 4 broker-common classes in `broker/`; keep `gateway/runtime/` (import follows `common`→`forwarding/common`); keep the build single-module + `@Profile` (multi-module deferred). Supersedes ADR-0162. Pure package re-organisation + ArchUnit-rule evolution — no behaviour/profile/bean-name/build change.

## What was signed off (G4, 2026-07-16)

User confirmed all 5 branches ("确认,5 分支全按方案"): Q1 single-module+@Profile (multi-module deferred) / Q2 event-bus wiring → `forwarding/runtime/relay/` / Q3 broker SPI → `forwarding/spi/broker/` + common stays `broker/` + rocketmq → `broker/rocketmq/` / Q4 SDK package-set / Q5 new ADR-0163 supersedes 0162 + replaces the rule.

## What drifted and was accepted (G6)

All Matched ✓ architecturally + **4 Drifted ⚠** (all accepted):

1. **Rule scope narrowed (user-accepted at a separate G5 gate):** the signed-off `gateway↛forwarding.runtime` was **infeasible** (the gateway legitimately wires `forwarding.runtime.{broker, jdbc, transport}` as a producer/consumer). As-built: `gateway.runtime ↛ forwarding.runtime.relay` (the relay-wiring home — the real plane boundary). A stricter rule (via a forwarding auto-config) is a flagged follow-on.
2. **4 ArchUnit tests evolved, not 2** — folding Spring/RocketMQ-using wiring INTO `forwarding..` (from the exempt `eventbus.runtime`/`common` planes) brought it under the `forwarding..` purity rules; exemptions added mirroring the existing `persistence.jdbc`/`transport.broker` pattern.
3. **5 tests MOVED (build-forced by package-private access)** — the 4 rocketmq tests (call `sql92Expression`/`buildMessage`/`drainAll` seams) → `broker/rocketmq/`; `EventBusRelaySchedulerTest` → `forwarding/runtime/relay/` (mirrors `RelayScheduler`). 5 broker tests stayed import-only.
4. **`RocketMqBrokerForwardingConsumer`/`Relay` reference NO broker-common types** (favorable — fewer imports than the to-be anticipated; SPI-only).

Zero Missing ✗, zero Surprise ⚠⚠. See `deviations.md` + `as-built/module-diff.md`.

## Verification (DoD)

- `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (BUILD SUCCESS; ArchUnit test count net 0 — 2 boundary tests replaced by 2; the 14 skipped = the 3 `RealBroker*` IT, env-guarded).
- All 4 evolved ArchUnit tests green (incl. `gateway↛forwarding.runtime.relay` + liveness guard + the purity exemptions + the rocketmq `..`-wildcard coverage).
- Working tree: 16 renames (11 source + 5 test moves) + 17 modified (staying referencers + 4 arch tests) + this delta's artefacts. Committed on `experimental` (separate from the prior 8 unpushed — 6 relay-scheduler + 2 agent-bus-layering). Push deferred per session convention.

## Follow-ups (not blocking)

- **Stricter rule via forwarding auto-config** (Q5 follow-on): add a `forwarding-runtime` auto-config that provides the broker-adapter + jdbc-outbox beans, so `GatewayRuntimeConfiguration` injects them + depends only on `forwarding.spi`+`forwarding.common` → `gateway↛forwarding.runtime` holds literally. Out of scope for this reorg; flagged in ADR-0163.
- **Maven multi-module** (Q1 follow-on): split `agent-bus` into `agent-bus-forwarding-sdk` + `-gateway` + `-eventbus` sub-modules (3 artifacts). The package reorg prepares the boundary; the build split is a deferred follow-on.
- `docs/adr/ADR-CLASSIFICATION.md` registry still stale (stops at 0068; missing 0161/0162/0163). Followed the 0161/0162 precedent (ADR file without a classification row); bulk resync is a separate cleanup.
