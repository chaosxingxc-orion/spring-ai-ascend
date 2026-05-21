# 0028. Causal Payload Envelope and Semantic Ontology Tags

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-12
**Technical story:** Fifth architecture reviewer raised Finding 1: raw `Object resumePayload` causes
"semantic collateral poisoning" — the system cannot distinguish real data (FACT) from synthesised
placeholders (PLACEHOLDER) or speculative hypotheses (HYPOTHESIS) when constructing LLM context.
Self-audit of Category A surfaced HD-A.4 (`AgentLoopDefinition.initialContext : Map<String,Object>`),
HD-A.3 (`ReasoningResult.payload : Object`), and HD-A.11 (no semantic-ontology schema anywhere).
ADR-0022 (PayloadCodec SPI) addresses *shape* typing. This ADR adds the *content-semantic* layer
above it. All implementation is deferred to W2.

## Context

The platform is a financial-services agent runtime where LLM hallucinations can produce harmful
outputs if fake or desensitised data masquerades as a verified fact. Two failure modes:

1. **Semantic collateral poisoning**: an intermediate node returns a `PLACEHOLDER` (desensitised PII
   substitution such as `"REDACTED_ACCOUNT_NO_42"`) into a child agent's context. The child agent
   treats it as a `FACT` and reasons over it, producing nonsensical or misleading outputs.

2. **Context explosion / OOM**: an oversized payload (e.g., a full document blob returned by a RAG
   node) is passed inline through `SuspendSignal.resumePayload` → `Checkpointer.save` → W2 Postgres
   checkpoint row, violating the §4 #13 16-KiB inline limit.

ADR-0022 (W2) defines `PayloadCodec<T>` + `EncodedPayload` for *how* a payload is serialised.
It does not specify *what* the payload semantically represents, nor whether it may appear in an LLM
prompt. This ADR defines that layer.

§4 #13 already specifies the 16-KiB cap contractually. `InMemoryCheckpointer` enforces it at W0
via a posture-aware precommit check (shipped, see `architecture-status.yaml: payload_fingerprint_precommit`).

## Decision Drivers

- Privacy-mode placeholder injection (desensitised PII) must not silently become an LLM `FACT`.
- The §4 #16 PII filter hook depends on field-typed payload (ADR-0022); semantic tagging is the
  complementary content-layer that the PII filter uses to decide which fields are sensitive.
- Payload fingerprint (SHA-256 + size) is a precommit gate: if fingerprint mismatches at resume,
  the orchestrator detects checkpoint tampering or corruption.
- Logical decaying (stripping heavy content to a reference pointer) implements the §4 #13 overflow
  contract in a structured way rather than a hard rejection.

## Considered Options

1. **`SemanticOntology` tag on `CausalPayloadEnvelope`** wrapping `Payload` from ADR-0022 (this
   decision).
2. **Annotation-based** (`@Fact`, `@Placeholder`) on domain record fields.
3. **No semantic tagging** — leave to application-level conventions.

## Decision Outcome

**Chosen option:** Option 1 — layered `CausalPayloadEnvelope` above ADR-0022 `Payload`.

### SPI shapes (design-only at W0; shipped at W2)

```java
// com.huawei.ascend.runtime.orchestration.spi — pure java.*

/** Epistemic classification of data entering the agent context or checkpoint. */
public enum SemanticOntology {
    /** Externally verified or system-persisted data — safe to use as LLM context fact. */
    FACT,
    /** Desensitised substitution of real PII — MUST NOT be interpreted as literal fact. */
    PLACEHOLDER,
    /** LLM-synthesised hypothesis — unverified; flag before acting. */
    HYPOTHESIS,
    /** Redacted at rest; reference pointer only — resolved before use. */
    REDACTED
}

/**
 * Content-semantic envelope wrapping any {@link Payload} (from ADR-0022).
 *
 * <p>Every payload that crosses a suspend/resume boundary at W2+ MUST be wrapped in a
 * {@code CausalPayloadEnvelope} with an explicit {@link SemanticOntology} tag. Raw
 * {@link Payload.RawPayload} envelopes are permitted only within a single JVM (W0 in-memory).
 *
 * <p>Logical decay: when {@code byteSize} exceeds the §4 #13 16-KiB inline cap, the
 * {@code payload} is replaced with a {@link Payload.RawPayload} containing only a
 * {@code PayloadStoreRef} (content-addressed pointer), and {@code decayed} is set to {@code true}.
 * Consumers MUST resolve the pointer via {@link PayloadStore} before use.
 */
public record CausalPayloadEnvelope(
        Payload payload,               // From ADR-0022 sealed Payload hierarchy
        SemanticOntology ontology,     // HD-A.11: content-semantic tag
        String payloadFingerprint,     // SHA-256 hex of the encoded bytes — precommit gate
        long byteSize,                 // size of encoded bytes
        boolean decayed                // true when payload is a reference pointer
) {}
```

### Logical decay contract

Before persisting a checkpoint:
1. Encode `payload` via `PayloadCodecRegistry.resolve(type)` → `EncodedPayload`.
2. Compute `payloadFingerprint = sha256(EncodedPayload.bytes)`.
3. If `EncodedPayload.bytes.length > 16_384`:
   a. Store bytes to `PayloadStore` (content-addressed).
   b. Replace payload with `RawPayload(new PayloadStoreRef(contentHash, typeRef))`.
   c. Set `decayed = true`.
4. Persist `CausalPayloadEnvelope` (envelope header, not the full bytes) with the checkpoint.

### Extension to ADR-0022 W2 scope (HD-A.3 + HD-A.4)

ADR-0022 documented `NodeFunction.apply(RunContext, Object)` and `SuspendSignal.resumePayload` in
its W2 evolution scope. This ADR adds two surfaces missed there:

- **`ReasoningResult(boolean terminal, Object payload)`** (`ExecutorDefinition.java:61-64`) —
  W2 evolution: `ReasoningResult(boolean terminal, Payload payload)`.
- **`AgentLoopDefinition.initialContext : Map<String, Object>`** (`ExecutorDefinition.java:39`) —
  W2 evolution: `AgentLoopDefinition.initialContext : CausalPayloadEnvelope` (ontology `FACT`
  at construction by the caller). A factory method `AgentLoopDefinition.withFact(Map<String,Object>)`
  wraps the existing `Map<String,Object>` path for backward compatibility.

### Placeholder exemption rule

A payload tagged `PLACEHOLDER` MUST pass through the LLM context *unchanged* and without triggering
the PII filter (it is already desensitised). The PII filter hook (§4 #16) inspects `ontology` before
deciding whether to apply field-level redaction. The `PLACEHOLDER` and `REDACTED` tags are an
exemption signal — no further redaction needed, and no "fact-based" reasoning must be triggered.

### Consequences

**Positive:**
- Downstream nodes can distinguish synthesised data from authoritative data without out-of-band
  conventions.
- The PII filter hook (§4 #16, ADR-0022 dependency chain) has a stable attach point.
- Payload fingerprint enables checkpoint tamper-detection; mismatch on resume fails fast rather
  than silently resuming over corrupted state.
- Logical decay keeps checkpoints within the §4 #13 inline cap without rejecting large payloads.

**Negative:**
- Every W2+ node must populate `SemanticOntology` — library authors must be aware.
- `CausalPayloadEnvelope` adds a small overhead header to every persisted payload; acceptable for
  financial-services audit requirements.

### Reversal cost

Medium — `CausalPayloadEnvelope` is a new type in the SPI layer; removing it at W2+ requires
updating all executor implementations. The `SemanticOntology` enum is additive; new values can be
added without breaking existing code.

## Pros and Cons of Options

### Option 1: CausalPayloadEnvelope + SemanticOntology (chosen)
- Pro: explicit, compiler-enforced, self-documenting.
- Pro: composed cleanly above ADR-0022 Payload hierarchy.
- Con: every node must opt-in to tagging.

### Option 2: Annotation-based
- Pro: zero-overhead at runtime for un-annotated records.
- Con: requires reflection or annotation processor; breaks SPI purity (java.* only) if using Spring annotations.

### Option 3: No tagging
- Pro: simplest.
- Con: no defence against semantic poisoning; PII filter has no attach point.

## References

- Fifth-reviewer document: `docs/logs/reviews/spring-ai-ascend-implementation-guidelines-en.md` §1
- Response document: `docs/logs/reviews/2026-05-12-fifth-reviewer-response.en.md` (Cat-A)
- ADR-0022: PayloadCodec SPI and typed payload contract
- ADR-0019: SuspendReason taxonomy (HD-A.3 resume-payload schema per-variant)
- ADR-0039: normative migration path `Object → Payload → CausalPayloadEnvelope` with `PayloadAdapter.wrap(Object)` (seventh-reviewer Cluster 6)
- §4 #13 (payload addressing and serialization contract — 16-KiB inline cap)
- §4 #21 (PayloadCodec SPI — parent of this ADR)
- §4 #25 (new, this ADR)
- §4 #36 (payload migration adapter strategy)
- `architecture-status.yaml` rows: `causal_payload_envelope`, `semantic_ontology_tags`, `payload_fingerprint_precommit`
- W2 wave plan: `docs/archive/2026-05-13-plans-archived/engineering-plan-W0-W4.md` §4.2 (archived per ADR-0037)
