package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractAgentRuntimeHandlerTest {

    @Test
    void runtimeAgentBaseKeepsExecutionConcernsOnly() {
        AbstractAgentRuntimeHandler agent = new StubRuntimeAgent();

        assertThat(agent).isInstanceOf(AgentRuntimeHandler.class);
        assertThat(agent).isNotInstanceOf(AgentCardProvider.class);
        assertThat(agent.agentId()).isEqualTo("weather-agent");
        assertThat(agent.isHealthy()).isTrue();
    }

    @Test
    void runtimeAgentRejectsBlankIdentityFields() {
        assertThatThrownBy(() -> new InvalidRuntimeAgent())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    private static final class StubRuntimeAgent extends AbstractAgentRuntimeHandler {

        private StubRuntimeAgent() {
            super("weather-agent");
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of("ok");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }

    private static final class InvalidRuntimeAgent extends AbstractAgentRuntimeHandler {

        private InvalidRuntimeAgent() {
            super(" ");
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.empty();
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }
}
