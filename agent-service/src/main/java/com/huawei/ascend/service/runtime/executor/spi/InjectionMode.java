package com.huawei.ascend.service.runtime.executor.spi;

/**
 * Wiring choice an {@link ExecutorAdapter} declares at registration.
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public enum InjectionMode {

    /** Native Agent — DI-injected platform beans (PlatformChatClient etc). */
    NATIVE_DI,

    /** Third-party framework — Memory / Model / Toolkit abstractions replaced by platform bridges. */
    THIRD_PARTY_BRIDGE,

    /** Asynchronous interrupt / approval relayed through the IEQ event channel. */
    EVENT_RELAY,

    /** Remote Agent — out-of-process via A2A; local M6 interception N/A. */
    NONE
}
