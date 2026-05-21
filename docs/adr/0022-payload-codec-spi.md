# 0022. PayloadCodec SPI and Typed Payload Contract

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Third architecture reviewer raised Issue 6: payloads are raw `Object` throughout the entire pipeline — `NodeFunction.apply(RunContext, Object) → Object`, `SuspendSignal.resumePayload: Object`, `Orchestrator.run(..., Object) → Object` — blocking serialization, API schema generation, and streaming event typing. Self-audit surfaced three additional gaps: (HD-D.1) `Checkpointer.save(byte[])` carries no codec metadata; (HD-D.2) `RunEvent` (§4 #11 streaming) has no class; (HD-D.3) PII redaction hooks depend on typed payload. This ADR defines the typed payload contract and `PayloadCodec` SPI. All implementation is deferred to W2.

## Context

W0 uses `Object` for all payloads. This is acceptable in-process (all components share the same JVM).
It becomes a defect at W2 (Postgres checkpoint must serialize/deserialize) and W4 (Temporal serializes
workflow arguments as its own `Payload` type).

`Checkpointer.save(UUID, String, byte[])` currently carries raw bytes with no codec identifier.
`IterativeAgentLoopExecutor` writes `payload.toString()` as bytes — an undocumented encoding that
`PostgresCheckpointer` at W2 cannot recover without out-of-band knowledge.

§4 #16 lists a `PII filter` as a W2 reference hook; a PII filter must inspect or redact payload
content — impossible with a raw `Object` that has no schema.

## Decision Drivers

- §4 #13 already commits: "when the durability tier crosses JVM boundaries, resumePayload MUST be serializable to bytes."
- §4 #11(e) commits: "typed progress events — no raw Object" for streaming.
- Rule 15 (deferred, W2 trigger) requires typed `RunEvent` shapes.

## Considered Options

1. **Jackson `ObjectMapper` hardcoded everywhere** — simple; ties the SPI to JSON; blocks Protobuf, Avro.
2. **Pluggable `PayloadCodec<T>` SPI with codec registry** (this decision) — flexible; default is Jackson JSON; operators register custom codecs.
3. **`java.io.Serializable` marker** — tied to JVM object graph serialization; not human-readable; conflicts with Temporal's own `Payload` type.

## Decision Outcome

**Chosen option:** Option 2 — pluggable `PayloadCodec<T>` SPI.

### SPI shapes (design-only at W0; shipped at W2)

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*

// Sealed carrier for in-process payloads
public sealed interface Payload permits Payload.TypedPayload, Payload.RawPayload {
    Class<?> type();

    record TypedPayload<T>(T value, Class<T> type) implements Payload {}

    // W0 backward-compat wrapper; rejected at persistence boundary by Rule 22
    record RawPayload(Object value) implements Payload {
        public Class<?> type() { return Object.class; }
    }
}

// Persistence form — carries codec metadata (HD-D.1)
public record EncodedPayload(byte[] bytes, String codecId, String typeRef) {}

public interface PayloadCodec<T> {
    String id();           // stable codec identifier, e.g. "jackson-json-v1"
    Class<T> type();
    EncodedPayload encode(T value);
    T decode(EncodedPayload encoded);
}

public interface PayloadCodecRegistry {
    <T> PayloadCodec<T> resolve(Class<T> type);
    PayloadCodec<?> resolveById(String codecId);
    void register(PayloadCodec<?> codec);
}
```

### RunEvent schema (HD-D.2)

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*
public sealed interface RunEvent
        permits RunEvent.NodeStarted, RunEvent.NodeCompleted,
                RunEvent.Suspended, RunEvent.Resumed,
                RunEvent.Failed, RunEvent.Terminal {

    UUID runId();
    Instant occurredAt();

    record NodeStarted(UUID runId, String nodeKey, Instant occurredAt)
            implements RunEvent {}
    record NodeCompleted(UUID runId, String nodeKey, EncodedPayload output, Instant occurredAt)
            implements RunEvent {}
    record Suspended(UUID runId, String parentNodeKey, SuspendReason reason, Instant occurredAt)
            implements RunEvent {}
    record Resumed(UUID runId, Instant occurredAt) implements RunEvent {}
    record Failed(UUID runId, String errorMessage, Instant occurredAt) implements RunEvent {}
    record Terminal(UUID runId, RunStatus finalStatus, Instant occurredAt) implements RunEvent {}
}
```

### NodeFunction evolution path

- W0: `NodeFunction.apply(RunContext ctx, Object payload) throws SuspendSignal` (unchanged).
- W2: signature evolves to `NodeFunction.apply(RunContext ctx, Payload.TypedPayload<?> payload)`.
- Backward-compat factory: `NodeFunction.wrap(java.util.function.Function<RunContext, Object>)`.

### Checkpointer evolution path (HD-D.1)

- W0: `Checkpointer.save(UUID, String, byte[])` — raw bytes, no metadata.
- W2: `Checkpointer.save(UUID, String, EncodedPayload)` — carries `codecId` + `typeRef`.
  W2 introduces `saveTyped(UUID, String, EncodedPayload)` first; raw-bytes `save` deprecated.

### PII filter dependency chain (HD-D.3)

The `PII filter` hook (§4 #16) operates as a `PayloadCodec<T>` pre-processor: before encoding,
the hook inspects `TypedPayload<T>` fields for PII annotations and redacts/replaces values.
Without `TypedPayload`, the hook cannot identify field boundaries.
Dependency chain: `PayloadCodecRegistry` → `PiiFilter<T>` → `RuntimeHook`. All deferred to W2.

### Rule 22 (deferred, W2 trigger)

Every payload that crosses a suspend/resume boundary (stored in `Checkpointer`, passed to
`PostgresOrchestrator.resume()`) MUST have a registered `PayloadCodec<T>`. `RawPayload` is
rejected at the persistence boundary with `IllegalStateException`.

### Consequences

**Positive:**
- W2 `Checkpointer.save(EncodedPayload)` is self-describing; `PostgresCheckpointer` deserializes without out-of-band knowledge.
- `RunEvent` gives streaming endpoints a stable type hierarchy from day 1.
- `PayloadCodec` registry enables Temporal's `DataConverter` to be implemented as a `PayloadCodec` adapter at W4.

**Negative:**
- W0 callers still pass raw `Object`; `RawPayload` wrapper needed transitionally.
- W2 `NodeFunction` signature change requires updating all W0 executor implementations.

### Reversal cost

Medium — the `Payload` sealed interface and `PayloadCodec` SPI are new types; removing them at W2+
requires updating all executor implementations again.

## References

- Third-reviewer document: `docs/logs/reviews/Architectural Perspective Review` (Issue 6)
- Response document: `docs/logs/reviews/2026-05-12-third-reviewer-response.en.md` (Cat-D)
- §4 #21 (typed payload + PayloadCodec SPI)
- Rule 22 (deferred, W2): PayloadCodec discipline
- `architecture-status.yaml` rows: `payload_codec_spi`, `typed_run_event_schema`, `payload_pii_filter_dependency`
- ADR-0039: normative migration path `Object → Payload → CausalPayloadEnvelope` with `PayloadAdapter.wrap(Object)` adapter wrapper requirement (seventh-reviewer Cluster 6)
