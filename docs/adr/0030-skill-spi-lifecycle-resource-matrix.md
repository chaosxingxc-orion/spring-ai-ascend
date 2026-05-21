# 0030. Skill SPI: Lifecycle, Resource Matrix, and Posture-Mandatory Sandbox

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Fifth architecture reviewer raised Finding 3: external skills lack Lifecycle
Sensing (init/suspend/teardown), explicit Resource Constraints (Cost/Token/Time/Concurrency), and a
mandatory sandbox for untrusted plugins. Systematic Category C audit surfaced 7 hidden defects:
no Skill SPI (HD-C.1); ResiliencePolicy missing tenant/token/cost axis (HD-C.2); §4 #16 HookChain
covers invocation interception but not lifecycle (HD-C.3); ADR-0018 sandbox is opt-in with no
posture-based mandate (HD-C.4); no skill-suspend path for long-horizon resource leak prevention
(HD-C.5); no per-skill cost receipt for P1 Rule 13 hook (HD-C.6); C/S fault-tolerance boundary
already covered by Rule 17 (HD-C.7, no new action). This ADR defines the Skill SPI, lifecycle
contract, ResourceMatrix, SkillTrustTier, and the posture-mandatory sandbox rule. Implementation
deferred to W2 (SPI) and W3 (mandatory sandbox gate).

## Context

At W0 the only "tool" abstraction is `NodeFunction.apply(RunContext, Object)` — a plain functional
interface with no lifecycle, no resource declaration, no isolation boundary, and no cost receipt.
Spring AI's `ToolCallback` / `@Tool` surface is not yet bound to any production code path.

When the platform adds external MCP tools, code-interpreter capabilities, and plugin-provided skills
at W2-W3, the absence of these contracts will cause:
- **Resource leaks**: a skill holding a DB connection or file handle has no guaranteed release path
  when a Run is suspended for 8 hours (HD-C.5).
- **Cost invisibility**: Rule 13 (P1 cost-of-use) requires token/cost tracking per skill; without a
  `SkillCostReceipt` return value, this is impossible (HD-C.6).
- **Sandbox bypass**: ADR-0018 `NoOpSandboxExecutor` is the default; an untrusted plugin running
  in-JVM can escape the platform's security perimeter in research/prod (HD-C.4).
- **Quota blindness**: `ResiliencePolicy(cbName, retryName, tlName)` has no tenant-quota or
  per-skill concurrency axis; §4 #12's `(tenantQuota × globalSkillCapacity)` two-axis model is
  designed but not bound to any SPI (HD-C.2).

## Decision Drivers

- Financial-services operators require per-skill audit trails and resource caps before any plugin
  is allowed on the platform.
- §4 #12 `skill_capacity_matrix` and §4 #16 `runtime_hook_spi` both assume a Skill SPI exists;
  this ADR creates it.
- Rule 13 (P1 — deferred W3) requires every LLM/tool call to return a cost receipt; binding this
  now prevents the W3 retrofit cost.
- The posture model (Rule D-6) already mandates fail-closed behaviour in research/prod; the untrusted-
  sandbox mandate is a natural extension of that model.

## Considered Options

1. **Full Skill SPI with lifecycle + ResourceMatrix + posture-mandatory sandbox** (this decision).
2. **Extend `NodeFunction` to add lifecycle callbacks** — simpler; avoids a new SPI type.
3. **Delegate entirely to Spring AI `ToolCallback`** — use Spring AI's existing tool abstraction.

## Decision Outcome

**Chosen option:** Option 1 — dedicated Skill SPI.

Spring AI `ToolCallback` has no lifecycle, no ResourceMatrix, and no trust-tier concept. Option 3
would bind the SPI to a Spring AI type, violating §4 #7 (SPI purity — java.* only). A thin adapter
from `ToolCallback` → `Skill` is the W2 composition pattern.

### SPI shapes (design-only at W0; shipped at W2)

```java
// com.huawei.ascend.runtime.skill.spi — pure java.*

/** Epistemic classification of a Skill's provenance and isolation requirements. */
public enum SkillTrustTier {
    /** Platform-vetted Java skill — may run in NoOpSandboxExecutor in any posture. */
    VETTED,
    /**
     * Untrusted plugin or user-provided code interpreter.
     * In research/prod posture MUST route through a non-NoOp SandboxExecutor (ADR-0018).
     */
    UNTRUSTED
}

/** Discriminates how a Skill is dispatched. */
public enum SkillKind {
    JAVA_NATIVE,              // Java class on the classpath; direct invocation
    MCP_TOOL,                 // Remote tool via MCP Java SDK (language-neutral)
    SANDBOXED_CODE_INTERPRETER // Code block via SandboxExecutor SPI (ADR-0018)
}

/**
 * Resource declaration for a Skill invocation.
 *
 * <p>Every Skill declares its worst-case resource consumption. The Orchestrator
 * validates declared limits before init() AND enforces the subset supported by the
 * dispatch path (see ADR-0038 §4 tiers). Hard-enforceable limits (token budget,
 * wall-clock timeout, concurrency cap, trust tier) are enforced at W2; CPU millis
 * and max-memory-bytes are sandbox-enforced only (UNTRUSTED + non-NoOp sandbox
 * required). The Run is suspended with {@link SuspendReason.RateLimited} when the
 * enforceable limits are exceeded (§4 #12).
 */
public record SkillResourceMatrix(
        String tenantQuotaKey,    // Key in the tenant-scoped ResiliencePolicy quota
        String globalCapacityKey, // Key in the platform-wide skill capacity limit
        long tokenBudget,         // Max LLM tokens this skill may consume per invocation
        long wallClockMs,         // Max wall-clock duration (maps to tlName in ResiliencePolicy)
        long cpuMillis,           // Max CPU time (for SandboxPolicy.maxCpuMillis)
        long maxMemoryBytes,      // Max heap (for SandboxPolicy.maxMemoryBytes)
        int concurrencyCap        // Max parallel invocations per tenant
) {}

/**
 * Per-invocation cost receipt returned by every Skill.execute() call.
 *
 * <p>Composes with Rule 13 (P1 cost-of-use, deferred W3): the Orchestrator accumulates
 * receipts from child Runs through {@link RunContext} budget propagation (§4 #12).
 */
public record SkillCostReceipt(
        long inputTokens,
        long outputTokens,
        long wallClockMs,
        long cpuMillis,
        String currencyCode,  // ISO 4217; null if not billable
        double cost           // USD (or currencyCode unit); 0.0 if not billable
) {
    public static SkillCostReceipt free() {
        return new SkillCostReceipt(0, 0, 0, 0, null, 0.0);
    }
}

/** Handle returned by Skill.suspend() to reconnect state after a long-horizon resume. */
public record SkillResumeToken(String tokenId, Object state) {}

/**
 * Context passed to all Skill lifecycle methods.
 */
public interface SkillContext {
    UUID runId();
    String tenantId();
    SkillResourceMatrix resourceMatrix();
}

/**
 * Core Skill SPI. Implementations must be registered in {@link CapabilityRegistry}
 * by name. All methods are called by the Orchestrator/Executor — never directly by
 * application code.
 *
 * <p>Lifecycle sequence:
 * <pre>
 *   init(ctx) → [execute(ctx, input) | suspend(ctx) → resume(ctx, token)]* → teardown(ctx)
 * </pre>
 *
 * <p>Resource guarantees:
 * <ul>
 *   <li>{@code teardown} is called even when {@code execute} throws — never skip it.
 *   <li>{@code suspend} is called exactly once before the Run transitions to SUSPENDED.
 *   <li>{@code resume} is called before the next {@code execute} after a suspension.
 * </ul>
 */
public interface Skill {

    /** Returns the stable, registry-resolvable name for this Skill. */
    String name();

    /** Skill provenance and isolation requirements. */
    SkillTrustTier trustTier();

    /** Resource declaration. Used by the Orchestrator for quota checks before init(). */
    SkillResourceMatrix resourceMatrix();

    /**
     * Allocates any resources this Skill needs (connections, file handles, thread locals).
     * Called exactly once per Run execution, before execute().
     */
    void init(SkillContext ctx) throws Exception;

    /**
     * Executes the skill's core logic.
     *
     * @return SkillCostReceipt — MUST reflect actual token/time consumption.
     */
    SkillCostReceipt execute(SkillContext ctx, Object input) throws Exception;

    /**
     * Called when the parent Run is about to be SUSPENDED (long-horizon wait).
     * The Skill MUST release all heavy resources (connections, file handles) and
     * return a SkillResumeToken encoding enough state to reconnect later.
     */
    SkillResumeToken suspend(SkillContext ctx);

    /**
     * Called on resume, before execute(). Reconnects resources using the token from suspend().
     */
    void resume(SkillContext ctx, SkillResumeToken token) throws Exception;

    /**
     * Releases all resources. Called unconditionally after execute() returns or throws.
     * MUST NOT throw.
     */
    void teardown(SkillContext ctx);
}
```

### Posture-mandatory sandbox rule (HD-C.4 + Rule 27)

| Skill.trustTier | dev posture | research posture | prod posture |
|---|---|---|---|
| VETTED | NoOpSandboxExecutor (default) | NoOpSandboxExecutor | NoOpSandboxExecutor |
| UNTRUSTED | NoOpSandboxExecutor + WARN log | MUST have non-NoOp SandboxExecutor; startup gate asserts | MUST have non-NoOp SandboxExecutor; startup gate asserts |

The startup gate (Rule 27, deferred W3): if any registered Skill has `trustTier = UNTRUSTED` and
the active `SandboxExecutor` bean is `NoOpSandboxExecutor`, the application MUST refuse to start in
research/prod posture (`IllegalStateException` in `@PostConstruct`).

### Integration with ResiliencePolicy (HD-C.2)

`ResiliencePolicy` currently models `(cbName, retryName, tlName)`. At W2, it is extended to a
two-axis record as named in §4 #12:

```java
// W2 extension — design-only at W0
public record ResiliencePolicy(
    String cbName,
    String retryName,
    String tlName,
    String tenantQuotaKey,    // HD-C.2: links to SkillResourceMatrix.tenantQuotaKey
    String globalCapacityKey  // HD-C.2: links to SkillResourceMatrix.globalCapacityKey
) {}
```

`ResilienceContract.resolve(operationId)` remains the **operation-policy routing** surface (W0+).
A SECOND OVERLOAD `resolve(tenant, skill)` was introduced in W1.x Phase 9 (ADR-0070, Rule R-K.b) for
**skill-capacity arbitration** — this overload **supersedes** the pre-ADR-0070 plan to extend the
operation surface to `(tenantId, operationId)`. The two axes (operation-policy routing vs
skill-capacity arbitration) MUST NOT be conflated. See ADR-0081 (Dual-Surface Reconciliation,
2026-05-18) for the formal reconciliation; this paragraph is amended in place rather than removed so
the original W2 evolution claim's history is preserved.

### Integration with HookChain (HD-C.3)

§4 #16 names six hook positions. Skill lifecycle hooks are DISTINCT from invocation-interception
hooks. They are not part of `HookChain` — they are called by the Orchestrator/Executor directly
via the `Skill` SPI lifecycle methods. This is intentional: lifecycle is synchronous and blocking;
hook chains are ordered and failsafe. Mixing them would conflate different failure semantics.

### Integration with SuspendReason (HD-C.5)

When a Run transitions to SUSPENDED (ADR-0019):
1. Orchestrator calls `Skill.suspend(ctx)` → receives `SkillResumeToken`.
2. Token is persisted in the executor checkpoint under a reserved key `_skill_resume_<skillName>`.
3. On resume, Orchestrator calls `Skill.resume(ctx, token)` before re-entering the executor.

The `SUSPENDED + suspendedAt + reason=RateLimited` path (§4 #12) also calls `Skill.suspend()` to
release resources while waiting for quota restoration.

### Consequences

**Positive:**
- No resource leaks on long-horizon suspend: `suspend()/teardown()` guarantee cleanup.
- `SkillCostReceipt` pre-wires Rule 13 (P1 cost-of-use) without a W3 retrofit.
- Posture-mandatory sandbox prevents untrusted code from bypassing isolation in production.
- `SkillKind` + `SkillTrustTier` ground the `CapabilityRegistry` vocabulary (HD-B.3).
- ResiliencePolicy extension formalizes the §4 #12 two-axis model.

**Negative:**
- Every W2 Skill implementation must implement 5 lifecycle methods; `AbstractSkill` adapter will
  provide no-op defaults for `suspend/resume` to reduce boilerplate.
- Spring AI `ToolCallback` adapters need a thin `ToolCallbackSkillAdapter` wrapper.

### Reversal cost

Medium — the Skill SPI is a new interface; removing it at W2+ requires updating all registered
capabilities. The `SkillTrustTier` enum is additive; new values can be added without breaking
existing code.

## References

- Fifth-reviewer document: `docs/logs/reviews/spring-ai-ascend-implementation-guidelines-en.md` §3
- Response document: `docs/logs/reviews/2026-05-12-fifth-reviewer-response.en.md` (Cat-C)
- ADR-0018: SandboxExecutor SPI (mandatory sandbox impl for UNTRUSTED skills)
- ADR-0019: SuspendReason taxonomy (SkillResumeToken + suspend path)
- ADR-0022: PayloadCodec SPI (SkillCostReceipt composes with PayloadCodec at persist)
- ADR-0029: Cognition-Action Separation (defines SkillKind taxonomy)
- §4 #12 (two-axis resource arbitration — skill_capacity_matrix)
- §4 #16 (HookChain — invocation interception, distinct from lifecycle)
- §4 #27 (new, this ADR)
- Rule 16 (deferred W2 — Cognitive Resource Arbitration)
- Rule 26 (deferred W2 — Skill Lifecycle Conformance, defined in CLAUDE-deferred.md)
- Rule 27 (deferred W3 — Untrusted Skill Sandbox Mandate, defined in CLAUDE-deferred.md)
- `architecture-status.yaml` rows: `skill_spi_lifecycle`, `skill_resource_matrix`, `untrusted_skill_sandbox_mandatory`
- W2 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §4.2 (archived per ADR-0037)

## Forward note — operates within ADR-0052 distributed scheduler (whitepaper-alignment remediation, 2026-05-13)

The Skill SPI lifecycle (`init / execute / suspend / teardown`) and `SkillResourceMatrix` defined here are the **Java SPI layer** — the inside of one capability, at the JVM level. Per ADR-0052 (Skill Topology Scheduler and Capability Bidding, §4 #50), the platform also defines a **distributed scheduling layer** above this Java SPI:

- `CapabilityRegistry` (with tenant-scoped pre-authorization and domain permission identifiers).
- `BidRequest` / `BidResponse` (capability bidding among eligible delegates).
- `PermissionEnvelope` (cascading issuance of action/tool permissions to the winning delegate, with subsumption boundary and short expiry).
- `SkillSaturationYield` (yields the dependent step via `SuspendReason.RateLimited`, releasing the LLM inference thread).

The two layers compose: ADR-0030's Java SPI describes how one skill behaves locally; ADR-0052's distributed contract describes how skills are scheduled, bid for, and authorized across the cluster. Tenant quota × global skill capacity arbitration (whitepaper §4.1 two-axis) lives at the ADR-0052 layer.
