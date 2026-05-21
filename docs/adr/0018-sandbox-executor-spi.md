# 0018. Sandbox Executor SPI for ActionGuard Bound Stage

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Competitive analysis confirmed SAA ships an experimental GraalVM 24.2.1-based code-execution sandbox and AS-Java ships `agentscope-runtime-java` with K8s + Alibaba FC backends. Our W3 ActionGuard Bound stage has no sandbox primitive. This ADR adds a pluggable SPI.

## Context

The W3 `ActionGuard` filter chain has five stages:
1. Authenticate (who is making the call?)
2. Authorize (is this caller allowed to call this tool?)
3. **Bound** (is this invocation within declared limits?)
4. Execute (perform the tool call)
5. Witness (record the audit entry)

The Bound stage currently enforces declarative limits (token budget, rate limit, time bound).
It has no mechanism to enforce *execution isolation* — preventing a tool invocation from
accessing arbitrary filesystem, network, or JVM internals. Without isolation:

- A malicious or buggy MCP tool plugin running in-JVM can escape the platform's security
  perimeter.
- Tools that execute user-provided code (eval, code-interpreter, data-analysis scripts) have
  no sandboxed execution context.

SAA's `spring-ai-alibaba-sandbox` uses GraalVM 24.2.1 polyglot isolation to confine code
execution within a GraalVM context with a restricted resource policy. AS-Java externalizes
sandbox entirely to `agentscope-runtime-java` (K8s pod + Alibaba FC serverless).

## Decision Drivers

- Financial-services deployments that allow code-interpreter tools MUST run those tools in
  isolation from the platform JVM.
- The GraalVM polyglot engine is available as a Maven dependency (`org.graalvm.polyglot`);
  it does not require a special JDK variant since GraalVM 23.1+ with JDK 21.
- A no-op default keeps the existing simple tool path unchanged for vetted Java tools that
  do not execute untrusted code.
- Containerized execution (Docker run / K8s job) is a valid alternative for higher isolation
  requirements; the SPI must accommodate both.

## Considered Options

1. Pluggable `SandboxExecutor` SPI on the Bound stage — no-op default, GraalVM + container
   as pluggable impls (this decision).
2. Always-on GraalVM isolation for all tool calls.
3. Keep no sandbox primitive; require operators to use container-per-tool deployment.

## Decision Outcome

**Chosen option:** Option 1 — pluggable `SandboxExecutor` SPI (W3).

The SPI shape:

```java
// in com.huawei.ascend.runtime.action.spi
public interface SandboxExecutor {
    // Returns the tool result or throws SandboxViolationException.
    Object execute(ActionRequest request, SandboxPolicy policy) throws SandboxViolationException;
}

public record SandboxPolicy(
    Set<String> allowedHosts,          // network allowlist
    long maxCpuMillis,                 // CPU time cap
    long maxMemoryBytes,               // heap cap for the sandboxed context
    boolean filesystemReadOnly         // restrict to read-only filesystem access
) {}

public final class SandboxViolationException extends RuntimeException { ... }
```

Two implementations shipped in W3:
- **`NoOpSandboxExecutor`** (default, `@ConditionalOnMissingBean`): executes the tool call
  directly in-JVM. Appropriate for vetted Java tools; logs at `DEBUG` that no isolation is active.
- **`GraalPolyglotSandboxExecutor`** (scaffold, not activated in default compose): wraps the
  tool call in a GraalVM 24.2+ Context with `HostAccess.NONE` + `Sandbox.ISOLATED` policy;
  enforces `SandboxPolicy.maxCpuMillis` and `maxMemoryBytes` via GraalVM resource limits.
  Requires `org.graalvm.polyglot:polyglot:24.2.+` on the classpath (provided via BOM, optional).

The Bound stage calls `sandboxExecutor.execute(request, resolvedPolicy)` and maps
`SandboxViolationException` → HTTP 403 + audit row with `reason=SandboxViolation`.

`sandbox_executor_spi` row in `architecture-status.yaml` tracks intent.

### Consequences

**Positive:**
- Simple tool paths (no untrusted code) remain unchanged; no GraalVM on the classpath for them.
- Operators who need isolation activate `GraalPolyglotSandboxExecutor` as a `@Bean` override.
- Container-based isolation is an equally valid `SandboxExecutor` impl (W4+) without SPI change.
- The Bound stage contract is explicit: tools that violate sandbox policy fail with a typed
  exception, not a silent JVM security manager bypass.

**Negative:**
- GraalVM polyglot sandbox is experimental for languages other than JS; Python/Ruby tool
  execution in the polyglot context is not guaranteed safe at GraalVM 24.2.
- Container-based isolation (K8s job per tool call) has much higher latency; the SPI must be
  async-capable before that impl is practical (deferred to W4+).

### Reversal cost

Low — SPI is an interface in `com.huawei.ascend.runtime.action.spi` (SPI purity enforced by
ArchUnit). Removing or replacing it is a clean cut.

## Pros and Cons of Options

### Option 1: Pluggable SPI, no-op default (chosen)

- Pro: safe default for existing simple tools.
- Pro: pluggable isolation for operators who need it.
- Pro: SPI allows container-based impl without API change.
- Con: no isolation unless operator explicitly configures a non-no-op executor.

### Option 2: Always-on GraalVM isolation

- Pro: isolation guaranteed for all tools.
- Con: GraalVM classpath for all deployments; startup overhead.
- Con: breaks pure-Java tools that use host reflection.
- Con: GraalVM polyglot still experimental for non-JS languages.

### Option 3: No sandbox primitive

- Pro: zero added complexity.
- Con: no isolation mechanism for code-interpreter tools.
- Con: explicit gap vs SAA sandbox + AS-Java runtime repo; competitive weakness remains.

## References

- SAA sandbox: [spring-ai-alibaba-sandbox (alibaba/spring-ai-alibaba)](https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-sandbox)
- AS-Java runtime: [agentscope-runtime-java (agentscope-ai)](https://github.com/agentscope-ai/agentscope-runtime-java)
- GraalVM Polyglot Sandbox: [GraalVM 24.2 Sandbox API](https://www.graalvm.org/jdk21/reference-manual/embed-languages/)
- competitive analysis: `docs/logs/reviews/2026-05-12-competitive-analysis-and-enhancements.en.md`
- `architecture-status.yaml` row: `sandbox_executor_spi`
- W3 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §5.2 (archived per ADR-0037)
