package com.huawei.ascend.runtime.engine.spi;

/**
 * Marker provider for framework state restore/export when a framework needs a
 * manual state bridge.
 *
 * <p>The runtime stores the neutral Agent State map through
 * {@code AgentStateStore}. A concrete agent framework implements this provider
 * only when it needs lifecycle hooks to translate that neutral map into its own
 * execution state. Frameworks with a native checkpointer, such as OpenJiuwen,
 * can instead use their own checkpointer configuration and skip this provider
 * for the main checkpoint path.
 */
public interface StateProvider extends AgentRuntimeProvider {
}
