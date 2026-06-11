package com.huawei.ascend.agentsdk.adapter.deepagent;

import com.huawei.ascend.agentsdk.adapter.react.OpenJiuwenRuntimeProof;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMessageAdapter;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implements {@link AgentRuntimeHandler} directly instead of extending
 * {@code OpenJiuwenAgentRuntimeHandler}: there is no openJiuwen DeepAgent
 * instance to hand to the base class until agent-core-java publishes the
 * DeepAgent APIs, so only the proof path is executable.
 */
public final class OpenJiuwenDeepAgentHandlerAdapter implements AgentRuntimeHandler {
    private final String agentId;
    private final boolean proofMode;
    private final OpenJiuwenRuntimeProof proof;
    private final OpenJiuwenMessageAdapter messageConverter = new OpenJiuwenMessageAdapter();
    private final OpenJiuwenStreamAdapter resultMapper = new OpenJiuwenStreamAdapter();

    public OpenJiuwenDeepAgentHandlerAdapter(
            String agentId,
            boolean proofMode,
            OpenJiuwenRuntimeProof proof) {
        this.agentId = agentId;
        this.proofMode = proofMode;
        this.proof = proof;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        try {
            Object input = messageConverter.toOpenJiuwenInput(context);
            if (proofMode) {
                return Stream.of(proof.run(input));
            }
            return Stream.of(Map.of(
                    "result_type", "error",
                    "output", "OpenJiuwen DeepAgent is temporarily disabled until agent-core-java publishes DeepAgent APIs"));
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getName() : error.getMessage();
            return Stream.of(Map.of("result_type", "error", "output", message));
        }
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult);
    }

    @SuppressWarnings("unchecked")
    private com.huawei.ascend.runtime.engine.spi.AgentExecutionResult mapRawResult(Object rawResult) {
        if (rawResult instanceof Map<?, ?> map) {
            return resultMapper.map((Map<String, Object>) map);
        }
        return resultMapper.map(Map.of("result_type", "answer", "output", String.valueOf(rawResult)));
    }
}
