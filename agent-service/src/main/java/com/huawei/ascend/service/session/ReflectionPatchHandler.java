package com.huawei.ascend.service.session;

import java.util.Map;

/**
 * Reflection patch handler.
 *
 * <p>Edge-side consumer of {@link com.huawei.ascend.bus.s2c.ReflectionEnvelopeRouter}
 * S2C deliveries. Applies the hot-patch to the active SessionContext
 * (e.g., appends summary message, mutates a variable, adjusts a
 * routing override).
 *
 * <p>Patch types dispatched by {@code patch_type} field per
 * {@code docs/contracts/reflection-envelope.v1.yaml#fields.patch_type}:
 * <ul>
 *   <li>{@code memory_append} — append a fact to long-term memory.</li>
 *   <li>{@code memory_replace} — replace a memory entry.</li>
 *   <li>{@code prompt_adjust} — tweak prompt prefix/suffix.</li>
 *   <li>{@code routing_override} — divert tool/model choice.</li>
 * </ul>
 *
 * <p>Confidence-gated: patches with confidence below the configured
 * threshold (default 0.7) are queued for human review rather than
 * auto-applied.
 *
 * <p>Reference impl lands as a follow-up alongside the Slow Track
 * judge wiring; this scaffold declares the contract.
 */
public interface ReflectionPatchHandler {

    /**
     * Apply (or queue) a reflection patch.
     *
     * @param reflectionEnvelope envelope shape mirroring the v1 YAML contract.
     * @return true if applied; false if queued for review.
     */
    boolean applyOrQueue(Map<String, Object> reflectionEnvelope);
}
