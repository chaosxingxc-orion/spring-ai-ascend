/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.verify.a2a;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A2A handler for the verify-agent. Loads the YAML-defined ReAct agent via
 * {@link AgentFactory#toReactAgent(Path)} — no programmatic builder usage.
 */
public final class VerifyAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    static final String AGENT_ID = "verify-agent";

    private final Path yamlPath;

    public VerifyAgentHandler(Path yamlPath) {
        super(AGENT_ID);
        this.yamlPath = Objects.requireNonNull(yamlPath, "yamlPath");
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        return AgentFactory.toReactAgent(yamlPath);
    }
}
