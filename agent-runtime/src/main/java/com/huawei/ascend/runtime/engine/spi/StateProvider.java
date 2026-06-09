package com.huawei.ascend.runtime.engine.spi;

/**
 * Marker provider for framework state restore/export.
 *
 * <p>The runtime still stores state through the neutral Agent State store. A
 * concrete agent framework implements this provider when it needs to translate
 * that neutral map into its own session/checkpoint mechanism.
 */
public interface StateProvider extends AgentRuntimeProvider {
}
