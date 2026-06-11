package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The lifecycle methods are defaults so every existing handler stays a valid
 * implementation: a handler that only implements the four execution-facing
 * methods must compile and the lifecycle surface must be callable no-ops.
 */
class AgentRuntimeHandlerTest {

    @Test
    void lifecycleMethodsDefaultToNoOps() {
        AgentRuntimeHandler handler = new AgentRuntimeHandler() {
            @Override
            public String agentId() {
                return "minimal";
            }

            @Override
            public boolean isHealthy() {
                return true;
            }

            @Override
            public Stream<?> execute(AgentExecutionContext context) {
                return Stream.empty();
            }

            @Override
            public StreamAdapter resultAdapter() {
                return raw -> Stream.empty();
            }
        };

        assertThatCode(() -> {
            handler.start();
            handler.cancel("task-1");
            handler.stop();
        }).doesNotThrowAnyException();
    }
}
