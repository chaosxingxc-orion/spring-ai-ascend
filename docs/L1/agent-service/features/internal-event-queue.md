---
level: L1
view: development
module: agent-service
status: design_only
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.3. Layer 3 is design_only per ADR-0141 — no `service.queue/` code home exists at rc55. This file describes the required capability boundary without implying existing runtime code."
---

# Internal Event Queue — Feature Inventory (AS-L1-F17..F23) — DESIGN_ONLY

> Module: Internal Event Queue (Layer 3 per ADR-0138; `design_only` per ADR-0141).
> Sovereign for: RunEvent envelope + channel routing, Producer/Consumer/Lease/Ack/Retry/DeadLetter semantics, three-track physical channel binding (Rule R-E), SSE/polling event projection material, resume-sweep rhythm, remote/child completion routing, configuration drift events.
> Status: **No code home on disk at rc55**; `service.queue/` is reserved per ADR-0141. Shipped channel transport lives in `agent-bus` (control/data/rhythm physical isolation per Rule R-E). This file specifies the design boundary so future implementation does NOT collapse channel intent into a single durability tier.

| Feature ID | Category | Covered clusters | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F17 | RunEvent envelope and channel routing | AS-SC09-AS-SC18, AS-SC23 | Define envelope and routing for RunCreated, RunStateTransition, SuspendRequested, ResumeRequested, S2C, ChildRun, CancelRequested, TerminalTransition, and third-party callback events. | Inputs: state / control boundary event. Outputs: routeable RunEvent envelope. | Session & Task Manager, Task-Centric Control Layer, agent-bus. | Missing tenant, wrong channel, payload over inline cap. | AutoGen message envelope, Temporal event history, Conductor task event. |
| AS-L1-F18 | Producer / Consumer / Lease / Ack / Retry / DeadLetter | AS-SC09-AS-SC12, AS-SC15-AS-SC18 | Split event publication and consumption, defining lease, ack, retry, dead-letter, heartbeat, dedup, and poison-message relationships. | Inputs: RunEvent, delivery receipt, consumer outcome. Outputs: ack, retry decision, dead-letter record. | Task-Centric Control Layer, Session & Task Manager, agent-bus. | Duplicate delivery, consumer crash, lease expiry, poison message. | Conductor worker poll/update/dead-letter, Temporal worker task queue. |
| AS-L1-F19 | Three-track physical channel binding | AS-SC09-AS-SC18 | Bind cancel / resume / suspend / S2C request / control signal to `control`; payload / transition / S2C response / child completion to `data`; heartbeat / tick / timeout to `rhythm`. | Inputs: event intent, payload size, timer signal. Outputs: control / data / rhythm channel operation. | Task-Centric Control Layer, physical deployment plane, agent-bus. | Control starvation, data congestion, timer loss, durability tier confusion. | Temporal signal/timer separation, Conductor queue visibility. |
| AS-L1-F20 | SSE / polling event projection | AS-SC02, AS-SC03, AS-SC09 | Provide event views that Access Layer can project into SSE streams or polling snapshots without treating client connections as the queue itself. | Inputs: RunEvent stream, cursor offset, query request. Outputs: stream projection, status projection. | Access Layer, Session & Task Manager. | Stream replay gap, slow consumer, stale polling snapshot. | A2A task event stream, OpenAI Agents stream event. |
| AS-L1-F21 | Resume sweep and long-running rhythm | AS-SC07, AS-SC09, AS-SC16 | Use rhythm ticks to express timeout, deadline, resume sweep, remote invocation check, heartbeat, and queue-lag observability. | Inputs: tick, lease state, remote handle, deadline. Outputs: timeout signal, resume check event, metric. | Task-Centric Control Layer, Session & Task Manager, Engine Dispatch & Execution. | Missed sweep, remote recovery not attempted, heartbeat lost. | Temporal timers, Conductor task timeout. |
| AS-L1-F22 | Remote / child completion routing | AS-SC15-AS-SC18 | Provide return-event boundaries for third-party Agents, peer Runs, and sub-agent child Runs completing, failing, cancelling, or timing out. | Inputs: remote callback, child terminal result, peer event. Outputs: ChildRunCompletedEvent, remote completion event, dead-letter. | Task-Centric Control Layer, Engine Dispatch & Execution, Access Layer. | Duplicate callback, late result, orphan child, parent cancelled. | A2A task update, AutoGen response envelope, CrewAI task output. |
| AS-L1-F23 | Configuration drift and audit events | AS-SC19-AS-SC24 | Emit auditable events for runtime drift in model, adapter, client capability, tool policy, and channel policy. | Inputs: resolved config snapshot, current config, drift detector output. Outputs: drift event, audit projection. | Session & Task Manager, Task-Centric Control Layer, Engine Dispatch & Execution, Translation & Tool-Intercept. | Silent config drift, adapter profile mismatch, unsupported hot update. | Temporal deterministic replay guard, Conductor workflow versioning. |

## Cross-references

- **Canonical Layer 3 definition**: [`../logical.md`](../logical.md) §1 (5-layer diagram — Layer 3 marked `(design_only — ADR-0141)`) + §7 (RunEvent sealed hierarchy per ADR-0145).
- **Scenario anchors**: [`../scenarios.md`](../scenarios.md) AS-SC09-AS-SC18 (long-running + remote/child).
- **Three-track invariant**: Rule R-E (`bus-channels.yaml` declares `control` / `data` / `rhythm` with physical isolation).
- **Physical channel mapping**: [`../physical.md`](../physical.md) §3 (three-track bus binding).
- **RunEvent contract**: `docs/contracts/run-event.v1.yaml` (status: `design_only` per ADR-0145).
- **SPI 4-way parity**: [`../spi-appendix.md`](../spi-appendix.md) — Layer 3 SPIs (Producer/Consumer/Lease/etc.) are NOT shipped at rc55; will appear when `service.queue/` lands.

## Originating source

This file absorbs §6.3 of [`docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md`](../../../logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md).
