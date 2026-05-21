package com.huawei.ascend.service.runtime.posture;

/**
 * Single construction-path posture utility for dev-only in-memory components (ADR-0035).
 *
 * <p>Rule 6: All posture-env reading is centralised here. No other class in
 * {@code com.huawei.ascend.service.runtime} may call {@code System.getenv("APP_POSTURE")} directly.
 *
 * <p>Pattern (§4 #32): dev → warn-and-continue; research/prod → throw IllegalStateException.
 */
public final class AppPostureGate {

    private AppPostureGate() {}

    /**
     * Asserts that the current posture is {@code dev}; warns if it is (non-durable reminder),
     * throws if posture is {@code research} or {@code prod}.
     *
     * @param componentName human-readable name for error/warning messages
     * @throws IllegalStateException if {@code APP_POSTURE} is {@code research} or {@code prod}
     */
    public static void requireDevForInMemoryComponent(String componentName) {
        checkPosture(componentName, System.getenv("APP_POSTURE"));
    }

    /**
     * Package-private overload for white-box unit tests that cannot manipulate env vars.
     * Called from {@code AppPostureGateTest}; do not call from production code.
     */
    static void checkPosture(String componentName, String posture) {
        if ("research".equals(posture) || "prod".equals(posture)) {
            throw new IllegalStateException(
                    componentName + " is a dev-posture in-memory component and cannot be used in " +
                    posture + " posture. Provide a durable implementation. See ADR-0035.");
        }
        System.err.println("[WARN] springai-ascend " + componentName +
                " is in-memory (dev-posture only, non-durable). " +
                "Set APP_POSTURE=research or prod to enforce durable implementations. See ADR-0035.");
    }
}
