# 0023. Cross-Boundary Context Propagation: Tenant, Trace, MDC, Metric Tags

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12

> **L1 update (2026-05-14).** Rule R-C.e was generalised from `TenantContextHolder` to the whole
> `com.huawei.ascend.service.platform..` package by ADR-0055. The original narrow rule below remains correct
> as the most-likely-violation case but is no longer the full contract. The generalised contract is
> asserted by `RuntimeMustNotDependOnPlatformTest`; the narrow case is preserved as defence-in-depth
> by `TenantPropagationPurityTest`. See `docs/adr/0055-permit-platform-to-runtime-direction.md`.
**Technical story:** Third architecture reviewer raised Issue 7: tenant propagation across suspend/resume boundaries is architecturally undefined. `TenantContextHolder` (ThreadLocal) is invalid across HTTP request boundaries, async threads, and timer-driven resumes. Self-audit surfaced five additional gaps: (HD-E.1) `RunContext.tenantId() : String` vs `TenantContext.tenantId() : UUID` type mismatch; (HD-E.2) Logback MDC not populated with `tenant_id`; (HD-E.3) no Micrometer tag policy mandating `tenant_id`; (HD-E.4) HTTP 403 mapping for resume mismatch is normative but unimplementable (no `RunController` yet — deferred); (HD-E.5) OTel `trace_id` propagation across suspend is unspecified. This ADR establishes a unified context propagation model.

## Context

§4 #3 says: "Every HTTP request must carry `X-Tenant-Id`. `TenantContextFilter` binds it to `TenantContextHolder`."
§4 #14 says: "resume request's tenant context MUST match the original `Run.tenantId`."

Between an HTTP request that creates a suspended Run and the subsequent resume request (or a timer-triggered
resume), the ThreadLocal is gone. The reviewer is correct: `TenantContextHolder` (`ThreadLocal<TenantContext>`)
is invalid across any of: (a) a new HTTP request, (b) an async thread pool thread, (c) a timer callback,
(d) a Temporal activity on a different JVM.

Two propagation mechanisms currently coexist:
1. `TenantContextHolder` — request-scoped, in `agent-platform`.
2. `RunContext.tenantId()` — per-run String, in `agent-runtime`.

Type inconsistency (HD-E.1): `TenantContext.tenantId() : UUID` vs `RunContext.tenantId() : String`.

## Decision Drivers

- `Run.tenantId` (a `String` field on the `Run` record, persisted to DB) is durable across all request
  and thread boundaries. It is the source-of-truth for timer-triggered resumes.
- `RunContext.tenantId()` is already the carrier used by all orchestration SPI internals — no
  production code in `agent-runtime` reads `TenantContextHolder`.
- Logback MDC is the standard mechanism for attaching contextual fields to JSON log lines.

## Considered Options

1. **Propagate tenant via `InheritableThreadLocal` for child threads** — doesn't work for timer-driven
   resumes; doesn't work for Temporal (different JVM).
2. **Canonicalize `RunContext.tenantId()` in `agent-runtime`; restrict `TenantContextHolder` to HTTP edge**
   (this decision) — clean scope separation; consistent with current code (runtime never reads the holder).
3. **Pass tenant as explicit method parameter everywhere** — already done in `Orchestrator.run(runId, tenantId, ...)`
   and `RunLifecycle.*`; this IS the current pattern for cross-boundary calls.

## Decision Outcome

**Chosen option:** Option 2 — `RunContext` is canonical in `agent-runtime`; `TenantContextHolder` is HTTP-edge-only.

### Canonical propagation model

```
HTTP path (create or explicit-resume):
  X-Tenant-Id header
  → TenantContextFilter (agent-platform, HTTP edge only)
      TenantContextHolder.set(...)           [request-scoped ThreadLocal]
      MDC.put("tenant_id", ...)              [log correlation — shipped now, HD-E.2]
  → RunController passes tenantId as String arg to Orchestrator.run(...)
  → SyncOrchestrator creates RunContextImpl(tenantId, runId, checkpointer)
  → RunContext.tenantId() is the canonical carrier inside agent-runtime

Timer-driven or internal resume:
  Scheduler / watchdog loads Run from RunRepository
  → Run.tenantId() is the source-of-truth
  → RunContextImpl constructed from Run.tenantId()
  → TenantContextHolder is NOT accessed
```

### Rule R-C.e — Tenant Propagation Purity (active, enforced now)

No class in `com.huawei.ascend.service.runtime.*` (main sources) may import
`com.huawei.ascend.service.platform.tenant.TenantContextHolder`.

Enforced by `TenantPropagationPurityTest` (ArchUnit rule, shipped at W0).

This rule applies to main sources only. Test classes may read the holder (e.g.,
`TenantContextFilterTest` which validates filter behavior directly).

### MDC population (HD-E.2, shipped now)

`TenantContextFilter.doFilterInternal` adds:
```java
MDC.put("tenant_id", uuid.toString());
// ... try { chain.doFilter(...) } finally { MDC.remove("tenant_id"); TenantContextHolder.clear(); }
```

Both branches (dev-default and normal UUID path) receive the MDC entry.

### Micrometer tenant tag policy (HD-E.3, deferred to W1)

Every custom metric under `springai_ascend_*` MUST carry a `tenant_id` tag. At W1, a `MeterFilter`
bean propagates the tag from `TenantContextHolder` (HTTP path) or `RunContext.tenantId()` (run-scoped
path) to all meters in that scope. Tracked as `micrometer_mandatory_tenant_tag`.

### RunContext.tenantId() type (HD-E.1, transitional)

`RunContext.tenantId()` currently returns `String`; `TenantContext.tenantId()` returns `UUID`. The
inconsistency is acknowledged and **not fixed at W0** to avoid cascading changes to `Run.tenantId`
(also `String`) and all callers. At W1, alongside Keycloak integration, both are changed to `UUID`.

### OTel trace propagation (HD-E.5, deferred to W2)

At W2, `Run` gains a `traceparent` column (W3C Trace Context). `RunController` writes the incoming
`traceparent` header to `Run.traceparent` at creation; on resume, the outgoing span uses the stored
`traceparent` to continue the distributed trace. Tracked as `otel_trace_propagation_across_suspend`.

### Consequences

**Positive:**
- Timer-driven and async resumes have a well-defined tenant source.
- MDC ensures `tenant_id` appears in every log line during an HTTP request.
- ArchUnit rule prevents future regressions where runtime code reads the ThreadLocal.
- OTel propagation is designed even though the implementation is deferred.

**Negative:**
- `RunContext.tenantId() : String` inconsistency persists until W1.
- Micrometer tag enforcement deferred to W1; some metrics may lack `tenant_id` until then.

### Reversal cost

Low — ArchUnit rule and MDC addition are small; the canonical-carrier decision requires no code changes
(runtime already uses `RunContext.tenantId()`).

## References

- Third-reviewer document: `docs/logs/reviews/Architectural Perspective Review` (Issue 7)
- Response document: `docs/logs/reviews/2026-05-12-third-reviewer-response.en.md` (Cat-E)
- §4 #22 (canonical run context propagation)
- Rule R-C.e (active): tenant propagation purity
- `architecture-status.yaml` rows: `tenant_propagation_purity`, `logbook_mdc_tenant_id`,
  `micrometer_mandatory_tenant_tag`, `otel_trace_propagation_across_suspend`
- W1 wave plan: Keycloak integration + UUID tenantId migration
- W2 wave plan: async orchestrator + OTel
