---
affects_level: L1
affects_view: logical
proposal_status: review
authors: ["laiyunzhi"]
date: 2026-05-20
source: REVIEW/Opus4.6 L1 架构评审_20260520.md
related_adrs:
  - ADR-0023
  - ADR-0024
  - ADR-0057
  - ADR-0070
related_rules:
  - Rule 20
  - Rule 21
  - Rule 36
  - Rule 44
affects_artefact:
  - agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/NoOpAsyncRunDispatcher.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/idempotency/IdempotencyHeaderFilter.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/tenant/TenantContextHolder.java
  - agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/SyncOrchestrator.java
  - docs/contracts/openapi-v1.yaml
---

# L1 Architecture Implementation Review

> English translation of `REVIEW/Opus4.6 L1 架构评审_20260520.md` (2026-05-20).

---

## Issue 1: HTTP Edge Not Wired to Orchestrator — Runs Stuck in PENDING

**Severity: P0**

**Code facts:**

```java
// RunController.java:107-109
CompletableFuture.runAsync(() -> fixedDispatcher.dispatch(dispatched));
return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(RunCursorResponse.from(saved, baseUrl(httpRequest)));

// NoOpAsyncRunDispatcher.java (default production implementation)
@Override
public void dispatch(Run run) {
    LOG.debug("NoOpAsyncRunDispatcher: dispatch called for runId={}", run.runId());
    // W2 supplies a real orchestrator-backed implementation (ADR-0070)
}
```

**Root cause:** After `POST /v1/runs` returns HTTP 202 + `TaskCursor`, `NoOpAsyncRunDispatcher` does nothing. The Run remains in `PENDING` forever. Clients polling `cursor_url` (i.e. `GET /v1/runs/{runId}`) always see `status: PENDING`.

This is **not** a “missing GET endpoint” problem — `GET /v1/runs/{runId}` already exists and is the intended poll target. The real gap is that the **dispatch → orchestration → state progression** chain is completely broken.

**Relationship to contracts:** OpenAPI correctly declares `POST /v1/runs` returns 202 (Rule 36 / ADR-0070) — that async semantics is right. The problem is not contract design; it is that **W1 did not ship a dispatcher implementation that advances Run state**.

**Additional risks:**

- `CompletableFuture.runAsync` uses the default `ForkJoinPool` — if a future dispatcher runs long LLM calls, it will contend on a shared pool.
- MDC is empty on the async thread (`run_id` is removed from MDC before 202 is returned), so dispatch logs cannot be correlated to the Run.
- `CreateRunRequest` only has `capabilityName`; `RunController` hard-codes `RunMode.GRAPH` — HTTP surface is inconsistent with dual-engine capability.

**Recommendations:**

| Priority | Action | Notes |
| -------- | ------ | ----- |
| Immediate | Implement `OrchestratingAsyncRunDispatcher` | Under dev posture, synchronously call `SyncOrchestrator.run()` to advance Run state |
| Immediate | Inject a named `Executor` | Do not use bare `CompletableFuture.runAsync`; use `@Bean Executor agentDispatchExecutor` |
| Immediate | Propagate trace context on async thread | At `dispatch(Run)` entry, populate MDC from `Run.traceId()` + `Run.tenantId()` |
| W2 | Add `mode` to `CreateRunRequest` | Or resolve mode from `capabilityName` via `EngineRegistry` |
| W2 | Add Run state timeout sweeper | `@Scheduled` scan for long-lived PENDING/RUNNING Runs; mark FAILED |

---

## Issue 2: Idempotency Body Cache Memory Pressure — Heap Risk Under Concurrency

**Severity: P2**

**Code facts:**

```java
// IdempotencyHeaderFilter.java:59
static final int MAX_CACHED_BODY_BYTES = 2 * 1024 * 1024; // 2 MiB

// IdempotencyHeaderFilter.java:171-186
private static byte[] readBodyCapped(HttpServletRequest request, int limit) throws IOException {
    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
    // ... read full body into memory ...
}
```

**Root cause:** Every POST/PUT/PATCH body is read fully into a `byte[]` to compute a SHA-256 hash. `CachedBodyHttpServletRequest` then holds that `byte[]` so downstream `@RequestBody` deserialization can read it again. That means:

1. The same body data can exist **twice** on the heap (cached `byte[]` + Jackson object graph) until GC.
2. 100 concurrent requests × 2 MiB ≈ 200 MiB transient heap (filter layer only).
3. Even when the request is ultimately rejected with 409 (idempotency conflict), the body has already been fully read.
4. `shouldNotFilter` has no `Content-Length` pre-check — a malicious client can send a 2 MiB body to consume server memory.

**Stated design rationale in comments:** “2 MiB comfortably covers the W1 max-http-form-post-size (1 MiB) without letting a malicious caller pin large buffers in heap before Spring's own size limits engage.” That is partially valid, but JSON body limits are usually not the same as form-post limits.

**Recommendations:**

| Priority | Action | Notes |
| -------- | ------ | ----- |
| Immediate | Add `Content-Length` pre-check in `shouldNotFilter` | If `Content-Length > MAX_CACHED_BODY_BYTES`, return 413 before `readBodyCapped` |
| Short term | Lower `MAX_CACHED_BODY_BYTES` to 256 KiB | Covers ~99% of JSON payloads; use streaming hash above the cap |
| Short term | Align with Spring `server.max-http-request-body-size` | Ensure filter cap ≤ Spring cap |
| W2 | Avoid re-caching on replay | If the same key returns a cached response, do not read the body again |

---

## Issue 3: `TenantContextHolder` Unavailable on Async Threads — Not a Bug, But Must Be Documented

**Severity: P3 (informational)**

**Code facts:**

```java
// TenantContextHolder.java:5
private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();
```

**Residual concern:** `RunController.create()` reads `TenantContextHolder.get()` on the HTTP request thread, then moves dispatch to another thread via `CompletableFuture.runAsync`. On the async thread, `TenantContextHolder.get()` returns `null` — this is not a bug by itself (tenant is passed explicitly via `Run.tenantId()`), but a future `AsyncRunDispatcher` that tries `TenantContextHolder.get()` will see `null`.

**Rule 21 safeguard:** `TenantPropagationPurityTest` (ArchUnit) forbids `service.runtime.*` from importing `TenantContextHolder`. `SyncOrchestrator` uses `RunContext.tenantId()` — correct design.

**Note:** The holder uses plain `ThreadLocal`, not `InheritableThreadLocal`. The risk is **missing** context on async workers, not unintended inheritance across virtual threads.

**Recommendations:**

| Priority | Action | Notes |
| -------- | ------ | ----- |
| Immediate | Document on `AsyncRunDispatcher` | “Implementations MUST NOT rely on `TenantContextHolder`; use `Run.tenantId()`” |
| Short term | ArchUnit guard | Forbid `InheritableThreadLocal` under `service.runtime.*` (prevent regression) |
| Long term | Evaluate `ScopedValue` (Java 21) | Aligns with ADR-0023; virtual-thread friendly |

---

## Issue 4: Generic `RuntimeException` in `SyncOrchestrator` Does Not Transition to FAILED — State Inconsistency

**Severity: P0**

**Code facts:**

```java
// SyncOrchestrator.java:164-175 — EngineMatchingException branch
} catch (EngineMatchingException eme) {
    if (run.status() != RunStatus.FAILED) {
        run = runs.save(run.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
    }
    hookDispatcher.fire(new HookContext(HookPoint.ON_ERROR, ...));
    throw eme;
}

// SyncOrchestrator.java:184-191 — generic RuntimeException branch
} catch (RuntimeException e) {
    hookDispatcher.fire(new HookContext(HookPoint.ON_ERROR, ...));
    throw e;  // ← no status transition! Run stays RUNNING
}
```

**Root cause:** If `NodeFunction` or `Reasoner` throws an ordinary `RuntimeException` (NPE, `IllegalArgumentException`, etc.), the Run stays **RUNNING** — it never becomes FAILED. The caller sees an exception, but `RunRepository` state is inconsistent.

**Contrast:** The S2C callback failure path (`SyncOrchestrator.java:108-128`) already handles the same class of problem — `RuntimeException` from `handleClientCallback` is caught and the Run is marked FAILED. The team fixed one branch but **left the generic branch open**.

**Recommendations:**

| Priority | Action | Notes |
| -------- | ------ | ----- |
| Immediate | Transition state in generic `RuntimeException` branch | `run = runs.save(run.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()))` |
| Immediate | Add integration test | `NodeFunction` throws NPE → assert FAILED + non-null `finishedAt` |
| W2 | Introduce retryable exception taxonomy | `@Retryable` or `RecoverableException`; retryable stays RUNNING until retry |
| W2 | Add `failureReason` on `Run` | Distinguish `engine_mismatch` / `node_function_error` / `s2c_timeout`, etc. |

**Minimal fix (~5 lines):**

```java
} catch (RuntimeException e) {
    if (run.status() != RunStatus.FAILED) {
        run = runs.save(run.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
    }
    hookDispatcher.fire(new HookContext(
            HookPoint.ON_ERROR, run.runId(), run.tenantId(),
            Map.of("exception", e.getClass().getName(),
                    "message", String.valueOf(e.getMessage()),
                    "reason", "unhandled_runtime_exception")));
    throw e;
}
```

---

## Issue 5: Fire-and-Forget Async Dispatch Lacks Completion Guarantees

**Severity: P1**

**Code facts:**

```java
// RunController.java:107
CompletableFuture.runAsync(() -> fixedDispatcher.dispatch(dispatched));

// SyncOrchestrator.java:77-79 (Javadoc)
// W0 atomicity invariant (ADR-0024): checkpoint write and RunRepository.save(suspended)
// are on the same call stack; single-threaded recursion ensures sequential ordering.
```

**The real problems are cross-thread visibility and completion guarantees:**

1. **HTTP thread:** save Run(PENDING) → return 202 → finish.
2. **ForkJoin thread:** `dispatcher.dispatch(run)` → (today NoOp — does nothing).
3. **If the JVM crashes before step 2:** Run stays PENDING forever; nothing recovers it.
4. **If the dispatcher throws:** the `CompletableFuture` exception is silently swallowed (no `exceptionally` handler).

**Note:** ADR-0024 ordering (checkpoint + suspend save on the same orchestrator call stack) still holds **inside** `SyncOrchestrator` on the worker thread. Async dispatch breaks **client-visible timing**, not internal suspend-path ordering.

**Recommendations:**

| Priority | Action | Notes |
| -------- | ------ | ----- |
| Immediate | Add `exceptionally` on `CompletableFuture` | On failure → mark Run FAILED + ERROR log |
| Immediate | Document in `SyncOrchestrator` Javadoc | “No crash recovery. Runs left PENDING/RUNNING after JVM restart are orphaned.” |
| W2 | Run state timeout sweeper | `@Scheduled` scan `updatedAt < now - threshold` for PENDING/RUNNING → EXPIRED |
| W2 | Same-transaction writes | Postgres-backed orchestrator writes Run + checkpoint in one `@Transactional` |

---

## Supplemental Issue 6: `cancel` Races with an In-Flight Run

**Severity: P2**

**Code facts:**

```java
// RunController.java:cancel()
Run current = found.get();
if (current.status() == RunStatus.CANCELLED) {
    return ResponseEntity.ok(RunResponse.from(current));  // idempotent
}
try {
    Run cancelled = current.withStatus(RunStatus.CANCELLED);
    Run saved = repository.save(cancelled);
    return ResponseEntity.ok(RunResponse.from(saved));
} catch (IllegalStateException ise) {
    return error(HttpStatus.CONFLICT, "illegal_state_transition", ...);
}
```

**Problem:** `cancel` runs on the HTTP thread while Run execution runs on a ForkJoin thread. Race example:

1. HTTP thread reads Run as RUNNING.
2. ForkJoin thread transitions Run to SUCCEEDED.
3. HTTP thread calls `withStatus(CANCELLED)` → `RunStateMachine.validate(SUCCEEDED, CANCELLED)` → ISE.

Current code correctly returns 409 via `catch (IllegalStateException)`. The deeper issue: **even when cancel successfully writes CANCELLED, in-flight `SyncOrchestrator.executeLoop` does not observe it** — execution continues until completion or an exception.

**Recommendations:**

- W2: cooperative cancellation — add `RunContext.isCancelled()`; executors check before each iteration.
- W1: document in `agent-service/ARCHITECTURE.md` that cancel only updates persistence and does not interrupt execution.

---

## Supplemental Issue 7: Idempotency Window Missing — `InMemoryStore` Has No TTL

**Severity: P2**

**Code facts:** `IdempotencyProperties` declares `ttl` (default `PT24H`), but `InMemoryIdempotencyStore` uses a plain `ConcurrentHashMap` with **no expiry**. Claimed keys never age out.

**Impact:** On long-running dev instances, legitimate retries (same `Idempotency-Key` + same body) may be rejected as 409 conflict.

**Recommendation:** Replace `ConcurrentHashMap` with a Caffeine `Cache` configured with `expireAfterWrite(ttl)`.

---

## Summary

| Severity | Count | Themes |
| -------- | ----- | ------ |
| P0 | 2 | Dispatcher not wired to orchestrator; fatal exceptions leave RUNNING |
| P1 | 1 | Fire-and-forget dispatch with no failure handling |
| P2 | 3 | Idempotency memory/TTL; cancel vs in-flight execution |
| P3 | 1 | Document `TenantContextHolder` vs async dispatch |

L1 **design** (SPI purity, formal DFA, strict `EngineRegistry` matching, middleware Hook chain, S2C callback protocol) remains strong. L1 **implementation** gaps concentrate on closing the HTTP → dispatch → orchestrator loop and making Run lifecycle state honest under failure and async execution.
