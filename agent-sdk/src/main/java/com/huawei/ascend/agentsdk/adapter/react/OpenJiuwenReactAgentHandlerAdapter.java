package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.singleagent.BaseAgent;

public class OpenJiuwenReactAgentHandlerAdapter extends OpenJiuwenAgentRuntimeHandler {
    private final BaseAgent agent;
    private final boolean proofMode;
    private final OpenJiuwenRuntimeProof proof;

    public OpenJiuwenReactAgentHandlerAdapter(
            String agentId,
            BaseAgent agent,
            boolean proofMode,
            OpenJiuwenRuntimeProof proof) {
        super(agentId);
        this.agent = agent;
        this.proofMode = proofMode;
        this.proof = proof;
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        return agent;
    }

    @Override
    protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
        if (proofMode) {
            return proof.run(input);
        }
        return super.runOpenJiuwenAgent(agent, input, conversationId);
    }
}
