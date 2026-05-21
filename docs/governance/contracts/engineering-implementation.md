---
level: L0
view: scenarios
scope_phase: impl
status: active
authority: "ADR-0098 (rc21 — 6-phase scenario-loaded contracts)"
---

# Phase Contract — Engineering Implementation

## When you enter this phase

You are about to:

- Write or edit production Java code under `agent-*/src/main/java/**`.
- Write or edit production yaml config (`application*.yml`, schemas
  under `docs/contracts/`, `docs/governance/*.yaml`).
- Author or amend a Flyway migration under `*/db/migration/`.
- Implement an SPI that was declared in the design phase.
- Wire up a `@ConfigurationProperties` block, an `AutoConfiguration`,
  or a `@Bean`.

Invoke `/impl-mode` (Wave 3) at phase entry. Until Wave 3 ships, Read
this file directly before starting.

## Active rules — impl phase

Markers: **P** = primary · **X** = cross-reference. See architecture-design.md
for the marker convention.

| Rule | Title | Marker | Card |
|---|---|---|---|
| D-1 | Root-Cause + Strongest-Interpretation Before Plan | **X** | [`rule-D-1.md`](../rules/rule-D-1.md) |
| D-2 | Simplicity & Surgical Changes | **X** | [`rule-D-2.md`](../rules/rule-D-2.md) |
| D-4 | Three-Layer Testing, With Honest Assertions | **P** | [`rule-D-4.md`](../rules/rule-D-4.md) |
| D-6 | Posture-Aware Defaults | **X** | [`rule-D-6.md`](../rules/rule-D-6.md) |
| D-7 | Concurrency / Async Resource Lifetime | **X** | [`rule-D-7.md`](../rules/rule-D-7.md) |
| D-8 | Single Construction Path Per Resource Class | **X** | [`rule-D-8.md`](../rules/rule-D-8.md) |
| D-9 | No Version / Log Metadata in Code | **X** | [`rule-D-9.md`](../rules/rule-D-9.md) |
| G-7 | Linux-First Dev Environment | **X** | [`rule-G-7.md`](../rules/rule-G-7.md) |
| G-10 | Parallel-Linux-Scripts Mandate | **X** | [`rule-G-10.md`](../rules/rule-G-10.md) |
| M-1 | Skeleton Module Has No Production Java | **X** | [`rule-M-1.md`](../rules/rule-M-1.md) |
| M-2 | Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth) | **X** | [`rule-M-2.md`](../rules/rule-M-2.md) |
| R-A | Business/Platform Decoupling Enforcement | **X** | [`rule-R-A.md`](../rules/rule-R-A.md) |
| R-C | Code-as-Contract | **X** | [`rule-R-C.md`](../rules/rule-R-C.md) |
| R-C.1 | Independent Module Evolution | **X** | [`rule-R-C.1.md`](../rules/rule-R-C.1.md) |
| R-C.2 | Run Contract Spine | **P** | [`rule-R-C.2.md`](../rules/rule-R-C.2.md) |
| R-D | SPI + DFX + TCK Co-Design + Catalog Integrity | **X** | [`rule-R-D.md`](../rules/rule-R-D.md) |
| R-E | Three-Track Channel Isolation | **X** | [`rule-R-E.md`](../rules/rule-R-E.md) |
| R-F | Cursor Flow Mandate | **X** | [`rule-R-F.md`](../rules/rule-R-F.md) |
| R-G | Reactive External I/O | **P** | [`rule-R-G.md`](../rules/rule-R-G.md) |
| R-H | No Thread.sleep in Business Code | **P** | [`rule-R-H.md`](../rules/rule-R-H.md) |
| R-I | Five-Plane Manifest | **X** | [`rule-R-I.md`](../rules/rule-R-I.md) |
| R-I.1 | Edge↔Compute Ingress Routing | **X** | [`rule-R-I.1.md`](../rules/rule-R-I.1.md) |
| R-J | Storage-Engine Tenant Isolation + Cancel Re-Authorization | **X** | [`rule-R-J.md`](../rules/rule-R-J.md) |
| R-K | Skill Capacity Matrix | **X** | [`rule-R-K.md`](../rules/rule-R-K.md) |
| R-L | Sandbox Permission Subsumption | **X** | [`rule-R-L.md`](../rules/rule-R-L.md) |
| R-M | Engine Contract (envelope / matching / hooks / S2C / scope / historical) | **X** | [`rule-R-M.md`](../rules/rule-R-M.md) |

## Forbidden patterns (this phase)

- Importing `org.springframework.web.client.RestTemplate` or
  `org.springframework.jdbc.core.JdbcTemplate` from
  `agent-service/.../runtime/**` (Rule R-G).
- Invoking `Thread.sleep(...)` or `TimeUnit.<unit>.sleep(...)` from
  `agent-service/.../platform/**` or `runtime/**` (Rule R-H); use
  `SuspendSignal` instead.
- Persisting a `Run` record without `tenantId` validated by
  `Objects.requireNonNull` (Rule R-C.2.a).
- Calling `Run.withStatus(newStatus)` without
  `RunStateMachine.validate(this.status, newStatus)` (Rule R-C.2.b).
- Importing `service.platform..` from `service.runtime..` (Rule R-C.2.c).
- Embedding version/log metadata (`# rc<N> Wave <M>`, `// per ADR-NNNN`,
  `# Finding F<N>`, commit SHAs) in production code — Rule D-9.
- Authoring a Flyway migration that creates a `tenant_id`-bearing table
  without enabling RLS in the same migration (Rule R-J.a).

## Exit criteria

- Unit tests pass: `./mvnw -pl <module> -am test` (Windows: `mvnw.cmd`).
- ArchUnit tests pass (they encode many of these forbidden patterns).
- Integration tests pass: `./mvnw verify` (uses Failsafe, not Surefire).
- Local gate parallel run is green for the touched rule scope: `bash
  gate/check_parallel.sh` on Linux/WSL per Rule G-7.
- No new entries added to `gate/d9-grandfathered-files.txt` (the
  grandfather list freezes today's debt; new code must be D-9-clean).

## Composes with

- Before implementation, the design phase should have already landed →
  `/design-mode` (if not, branch back).
- After implementation, gates and tests must be green → `/verify-mode`.
- When ready to package the change → `/commit-mode`.
