package com.huawei.ascend.service.agent;

import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.service.agent.spi.Agent;
import com.huawei.ascend.service.agent.spi.AgentDefinition;

/**
 * Bridges an {@link Agent} definition to an
 * {@link ExecutorDefinition} the {@code Orchestrator} can run.
 *
 * <p>Authority: ADR-0128 §Integration. Design-only shell.
 *
 * <p>The W3 SDK GA implementation translates an {@link AgentDefinition}
 * into either an {@code ExecutorDefinition.AgentLoopDefinition}
 * (ReAct-style loops) or an {@code ExecutorDefinition.GraphDefinition}
 * (when the Agent carries a {@code plannerBinding} that produces
 * a graph plan).
 */
public final class AgentExecutorDefinitionFactory {

    private AgentExecutorDefinitionFactory() {
    }

    public static ExecutorDefinition fromAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must be non-null");
        }
        throw new UnsupportedOperationException(
                "AgentExecutorDefinitionFactory: design-only shell at L0; "
                        + "W3 SDK GA translates AgentDefinition into "
                        + "ExecutorDefinition.AgentLoopDefinition (default) "
                        + "or ExecutorDefinition.GraphDefinition (when plannerBinding present)");
    }
}
