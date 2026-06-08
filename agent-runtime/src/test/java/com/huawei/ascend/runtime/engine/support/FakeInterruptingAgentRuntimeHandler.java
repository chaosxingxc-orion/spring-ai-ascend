package com.huawei.ascend.runtime.engine.support;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.InterruptType;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Test {@link AgentRuntimeHandler} that interrupts on the first run (EXECUTE) and
 * completes on the second (RESUME), keyed by an internal flag. Lets an
 * interrupt → resume round-trip be exercised entirely in-engine, without a real
 * agent framework.
 */
public class FakeInterruptingAgentRuntimeHandler implements AgentRuntimeHandler {

    private final String agentId;
    private boolean resumed;

    public FakeInterruptingAgentRuntimeHandler(String agentId) {
        this.agentId = agentId;
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
        if (!resumed) {
            resumed = true;
            return Stream.of(Map.of(
                    "result_type", "interrupt",
                    "interrupt_type", InterruptType.HUMAN_INPUT,
                    "prompt", "Need your confirmation"));
        }
        return Stream.of(
                Map.of("result_type", "output", "output", "done"),
                Map.of("result_type", "answer", "output", "final answer"));
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(FakeInterruptingAgentRuntimeHandler::adaptRawResult);
    }

    @SuppressWarnings("unchecked")
    private static AgentExecutionResult adaptRawResult(Object rawResult) {
        Map<String, Object> result = (Map<String, Object>) rawResult;
        String resultType = String.valueOf(result.get("result_type"));
        if ("interrupt".equals(resultType)) {
            return AgentExecutionResult.interrupted(
                    (InterruptType) result.get("interrupt_type"),
                    String.valueOf(result.get("prompt")));
        }
        String output = String.valueOf(result.get("output"));
        if ("answer".equals(resultType)) {
            return AgentExecutionResult.completed(output);
        }
        return AgentExecutionResult.output(output);
    }
}
