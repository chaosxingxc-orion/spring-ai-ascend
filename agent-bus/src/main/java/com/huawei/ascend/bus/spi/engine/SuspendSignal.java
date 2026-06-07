package com.huawei.ascend.bus.spi.engine;

import java.util.Objects;

/**
 * Checked exception that an executor throws to request suspension.
 *
 * <p>Two flavours, both caught only by the Orchestrator:
 * <ul>
 *   <li><b>Child-run suspension</b> (legacy, W0+): construct with
 *   {@code (parentNodeKey, resumePayload, childMode, childDef)}. The
 *   orchestrator persists checkpoint, marks parent SUSPENDED, and dispatches
 *   the child run.</li>
 *   <li><b>S2C client-callback suspension</b> (W2.x Phase 3, ADR-0074;
 *   refactored from parallel unchecked S2cCallbackSignal to this checked
 *   variant in v2.0.0-rc3 per cross-constraint audit α-2 / β-5): build via
 *   {@link #forClientCallback(String, Object)} where the {@code envelope}
 *   argument MUST be an instance of
 *   {@code com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope} (typed as
 *   Object here to preserve orchestration.spi purity per E3). Discriminate
 *   at the catch site via {@link #isClientCallback()} and cast
 *   {@link #clientCallback()} to the concrete envelope type. The orchestrator
 *   persists checkpoint, marks parent SUSPENDED with
 *   {@code SuspendReason.AwaitClientCallback}, dispatches the request through
 *   the registered {@code S2cCallbackTransport}, and resumes when the client
 *   returns.</li>
 * </ul>
 *
 * <p>ADR-0019's checked-suspension doctrine is preserved across both flavours:
 * every executor lambda already declares {@code throws SuspendSignal}, so the
 * Java type system pins both suspension paths at compile time.
 *
 * <p>Executors must not catch this exception.
 *
 * <p><b>Vocabulary Glossary.</b> Authority: ADR-0137 + ADR-0100.
 * Academic prose ("InterruptSignal" / "interrupt primitive") refers to THIS class.
 * ADR-0100 §rejected-framings #2 explicitly RETAINS the checked-exception
 * form of {@code SuspendSignal} as a Tier-A competitive differentiator — the Java
 * compiler enforces caller-side handling, Rule R-G ArchUnit guards depend on the
 * checked shape, and cancellation flows use exception-flow cross-thread
 * propagation. Synonym mapping:
 * <ul>
 *   <li>"InterruptSignal" ≡ {@code SuspendSignal} (this class)</li>
 *   <li>"InterruptReason" ≡ {@code SuspendReason} (sealed interface in
 *       {@code com.huawei.ascend.runtime.resilience.spi.SuspendReason})</li>
 *   <li>"Yield" / "ON_YIELD" ≡ a cooperative-scheduling yield hint (Authority:
 *       ADR-0100 §coexistence — does NOT trigger a state-machine transition,
 *       coexists alongside SuspendSignal)</li>
 * </ul>
 */
public final class SuspendSignal extends Exception {

    private final String parentNodeKey;
    private final Object resumePayload;
    private final RunMode childMode;             // null when isClientCallback()
    private final ExecutorDefinition childDef;   // null when isClientCallback()
    private final Object clientCallback;         // typed Object for SPI purity (E3); actual type
                                                  // is com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope.
                                                  // Orchestrators in non-spi packages cast it.

    /** Child-run constructor (legacy, W0+). All four args required. */
    public SuspendSignal(String parentNodeKey, Object resumePayload,
                         RunMode childMode, ExecutorDefinition childDef) {
        super("Suspend requested at node: " + parentNodeKey);
        this.parentNodeKey = Objects.requireNonNull(parentNodeKey, "parentNodeKey is required");
        this.resumePayload = resumePayload;
        this.childMode = Objects.requireNonNull(childMode, "childMode is required");
        this.childDef = Objects.requireNonNull(childDef, "childDef is required");
        this.clientCallback = null;
    }

    /**
     * S2C client-callback factory (W2.x Phase 3, ADR-0074).
     *
     * <p>{@code envelope} must be an instance of
     * {@code com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope} (relocated
     * from {@code com.huawei.ascend.runtime.s2c} in v2.0.0-rc3 per α-4 / β-2);
     * typed as Object here to preserve orchestration.spi purity (E3 archunit).
     * Orchestrators cast in non-spi packages.
     */
    public static SuspendSignal forClientCallback(String parentNodeKey, Object envelope) {
        return new SuspendSignal(parentNodeKey, envelope);
    }

    private SuspendSignal(String parentNodeKey, Object envelope) {
        super("S2C client callback at node: " + parentNodeKey);
        this.parentNodeKey = Objects.requireNonNull(parentNodeKey, "parentNodeKey is required");
        this.resumePayload = null;
        this.childMode = null;
        this.childDef = null;
        this.clientCallback = Objects.requireNonNull(envelope, "envelope is required");
    }

    public String parentNodeKey() { return parentNodeKey; }
    public Object resumePayload() { return resumePayload; }
    public RunMode childMode() { return childMode; }
    public ExecutorDefinition childDef() { return childDef; }

    /** True when this signal carries an S2C client callback (W2.x Phase 3). */
    public boolean isClientCallback() { return clientCallback != null; }

    /**
     * The S2C envelope; non-null only when {@link #isClientCallback()} is true.
     * Type-erased to Object per SPI-purity rule E3; orchestrators in non-spi
     * packages cast to {@code S2cCallbackEnvelope}.
     */
    public Object clientCallback() { return clientCallback; }
}
