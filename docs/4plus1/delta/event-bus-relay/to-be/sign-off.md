# G4 sign-off — event-bus two-hop relay gap

**Date:** 2026-07-15
**User confirmation (exact):** "确认 to-be，进 G5 实现 (推荐)" — AskUserQuestion selection on the G4 sign-off gate (affirmative + specific: confirms the to-be plan AND authorizes proceeding to G5 implement).
**Skill rule honored:** acceptance was affirmative and specific (not "差不多"/"嗯"); the to-be deltas below are locked as the G5 implementation baseline.

## Confirmed to-be (one paragraph)
Wire the agent-bus gateway + forwarding substrate into two launchable process forms off the single `AgentBusApplication` via Spring profiles: a gateway form (`@Profile(gateway)` wiring `GatewayRuntimeService` + the S4-deferred `POST /a2a` controller + response consumer) and an event-bus+registry form (`@Profile(eventbus)` wiring a new `EventBusRelayWorker` that consumes hop1, applies between-hops governance (inbox dedup + tenant check + correlation match + audit), and re-publishes hop2, with a symmetric relay on the response leg). The relay reuses all existing substrate ports (no new ports). `T_invocation_deliver/resp_in/resp_out` (provisioned-unused) get wired. Deployment adds spring-boot repackage (fat-jar) + docker-compose gateway/event-bus services. `A2aForwardingDeliveryPort` (T1 HTTP) becomes legacy (replaced, retained). A two-hop IT with the in-repo `TestAgentRuntime` double (env-guarded) exercises the governance-between-hops (today zero coverage). External agent-runtime-java hop2 parallel, not time-locked.

## Locked deltas vs as-is
- `++ EventBusRelayWorker` (consume→govern→re-produce; reuses Inbox/Outbox/Relay/Consumer ports + state machine/retry/breaker) — **Q1a**
- `++ GatewayRuntimeConfiguration @Profile(gateway)` + `GatewayRuntimeController POST /a2a` (S4-deferred)
- `++ EventBusRelayConfiguration @Profile(eventbus)`
- `++ RealBrokerTwoHopRelayIntegrationTest` (two-hop IT, in-repo double, env-guarded) — **Q4a**
- `++ agent-bus/pom.xml` spring-boot repackage (fat-jar) — **Q3a**
- `++ docker-compose.yml` gateway + event-bus services
- `~ AgentBusApplication` (single main; profiles select form; `java -jar` now works) — **Q2a**
- `~ GatewayRuntimeService` (wired bean; dispatchRequest hop1 produce)
- `~ JdbcForwardingInbox/Outbox` (wired for governance)
- `-- A2aForwardingDeliveryPort` (T1 HTTP legacy; replaced, retained for coexistence)

## G3 decisions carried into G5
Q1a (new relay from existing ports) · Q2a (Spring profiles) · Q3a (fat-jar + compose) · Q4a (in-repo double IT) · G3.5 skipped (pure backend).

## Load-bearing assumptions verified pre-G5 (L3)
1. `AgentBusApplication` boots only registry beans today (grep-verified G2). → G5 ADDs `@Profile`-gated beans.
2. No `spring-boot-maven-plugin <repackage>` anywhere (agent-verified G2). → G5 adds it to `agent-bus/pom.xml`.
3. No consume→govern→re-produce relay component exists (grep-verified G2). → G5 net-new `EventBusRelayWorker`.

## Implementation scope (handoff §3.4 sub-steps ②-⑥, post sign-off)
② assemble two-hop relay loop + symmetric response path (reusing existing blocks) → ③ process split (Spring profile) → ④ deployment artifacts (repackage + compose) → ⑤ two-hop IT → ⑥ external agent-runtime-java hop2 coordination (contract only; parallel, not time-locked). **Not a REQ-2026-001 release blocker.**

Sign-off achieved — proceeding to G5.
