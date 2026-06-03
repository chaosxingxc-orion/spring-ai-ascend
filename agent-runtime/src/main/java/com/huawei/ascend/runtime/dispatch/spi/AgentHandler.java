package com.huawei.ascend.service.engine.spi;

import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import java.util.stream.Stream;

/**
 * The seam between the engine and a concrete agent framework. An implementation
 * knows how to run one agent and surface its framework-neutral results. The
 * engine runtime owns lifecycle events and route-specific event mapping.
 */
public interface AgentHandler {

    /** The agent id this handler serves. */
    String agentId();

    /** Whether this handler is ready to execute. */
    boolean isHealthy();

    /** Run the agent for the given context, emitting framework-specific results. */
    Stream<?> execute(AgentExecutionContext context);

    /** Adapter that maps framework-specific results to engine-neutral results. */
    AgentResultAdapter resultAdapter();
}
