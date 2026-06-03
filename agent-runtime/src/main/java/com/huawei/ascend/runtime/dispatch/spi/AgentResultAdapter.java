package com.huawei.ascend.service.engine.spi;

import java.util.stream.Stream;

/**
 * Converts framework-specific agent results into the engine-neutral result
 * stream consumed by the dispatcher.
 */
@FunctionalInterface
public interface AgentResultAdapter {

    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
