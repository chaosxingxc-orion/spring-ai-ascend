package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.runtime.engine.adapters.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.stream.Stream;

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
    public Stream<?> execute(AgentExecutionContext context) {
        try {
            if (proofMode) {
                return Stream.of(proof.run(toOpenJiuwenInput(context)));
            }
            return Stream.of(Runner.runAgent(agent, toOpenJiuwenInput(context), null, null));
        } catch (RuntimeException error) {
            return Stream.of(java.util.Map.of(
                    "result_type", "error",
                    "output", errorMessage(error)));
        } finally {
            safeRelease(context);
        }
    }
}

