package com.huawei.ascend.service.runtime.resilience.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Reason envelope for a run-suspension, paired with {@code RunStatus.SUSPENDED}.
 * Sealed.
 *
 * <p>Variant maturity at W2.x Phase 3:
 * <ul>
 *   <li>{@link RateLimited} - implemented (W1.x Phase 9).</li>
 *   <li>{@link AwaitClientCallback} - implemented (W2.x Phase 3, ADR-0074).</li>
 *   <li>{@link AwaitChild}, {@link AwaitTimer}, {@link AwaitExternal}, {@link AwaitApproval}
 *       - declared placeholders so downstream code can write exhaustive switch
 *       statements; bodies land when each variant ships an enforcer.</li>
 * </ul>
 *
 * <p>Authority: ADR-0070 (Cursor Flow + Skill Capacity Runtime); ADR-0074 (S2C
 * Capability Callback Protocol); CLAUDE.md Rule R-K (Skill Capacity Matrix);
 * CLAUDE.md Rule R-K (legacy 41.b) (ResilienceContract runtime enforcement); CLAUDE.md
 * Rule 46 (S2C Callback Envelope + Lifecycle Bound).
 */
public sealed interface SuspendReason
        permits SuspendReason.RateLimited,
                SuspendReason.AwaitChild,
                SuspendReason.AwaitTimer,
                SuspendReason.AwaitExternal,
                SuspendReason.AwaitApproval,
                SuspendReason.AwaitClientCallback {

    /**
     * Skill-capacity pool was exhausted. The scheduler should park this agent process
     * on the affected skill's wait-queue and free the OS thread for unrelated work.
     *
     * @param skill the skill id that exhausted (matches {@code docs/governance/skill-capacity.yaml})
     * @param code  the canonical reason code; today only {@code SKILL_CAPACITY_EXCEEDED}
     */
    record RateLimited(String skill, String code) implements SuspendReason {
        public static final String SKILL_CAPACITY_EXCEEDED = "SKILL_CAPACITY_EXCEEDED";
    }

    /**
     * Server-to-Client capability invocation is in flight. The Run is suspended
     * until the client returns a response via the registered S2cCallbackTransport
     * or the deadline elapses. ADR-0074.
     *
     * @param callbackId    UUID matching the in-flight S2cCallbackEnvelope.callback_id
     * @param capabilityRef declared client capability id (e.g., "client.browser.screenshot")
     * @param deadline      absolute deadline; on elapsed without response, Run -> FAILED (s2c_timeout)
     */
    record AwaitClientCallback(UUID callbackId, String capabilityRef, Instant deadline)
            implements SuspendReason {
        public static final String S2C_RESPONSE_INVALID = "s2c_response_invalid";
        public static final String S2C_CLIENT_ERROR = "s2c_client_error";
        public static final String S2C_TIMEOUT = "s2c_timeout";
    }

    /** Placeholder for the four other ADR-0019 variants - bodies land per future phase. */
    record AwaitChild() implements SuspendReason {}
    record AwaitTimer() implements SuspendReason {}
    record AwaitExternal() implements SuspendReason {}
    record AwaitApproval() implements SuspendReason {}
}
