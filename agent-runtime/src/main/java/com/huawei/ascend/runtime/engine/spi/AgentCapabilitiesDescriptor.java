package com.huawei.ascend.runtime.engine.spi;

/**
 * Protocol-neutral descriptor for the capabilities advertised by an agent card.
 *
 * <p>All fields default to {@code false}; the A2A mapper sets the actual
 * wire values. This type carries zero {@code org.a2aproject} imports.
 */
public record AgentCapabilitiesDescriptor(
        boolean streaming,
        boolean pushNotifications,
        boolean extendedAgentCard) {

    /**
     * Fail-safe capability baseline: streaming off, pushNotifications off, extended off.
     * A handler opts in by overriding {@link AgentRuntimeHandler#supportsStreaming()} or by
     * supplying a durable {@code PushNotificationConfigStore} bean. This default prevents
     * the card from advertising capabilities the handler does not actually provide.
     */
    public static AgentCapabilitiesDescriptor defaults() {
        return new AgentCapabilitiesDescriptor(false, false, false);
    }

    /** All flags false — useful as an explicit zero baseline. */
    public static AgentCapabilitiesDescriptor none() {
        return new AgentCapabilitiesDescriptor(false, false, false);
    }
}
