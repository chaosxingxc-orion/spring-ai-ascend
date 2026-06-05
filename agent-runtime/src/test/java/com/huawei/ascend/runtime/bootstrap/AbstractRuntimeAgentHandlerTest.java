package com.huawei.ascend.runtime.bootstrap;

import com.huawei.ascend.runtime.dispatch.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.dispatch.spi.AgentHandler;
import com.huawei.ascend.runtime.dispatch.spi.AgentResultAdapter;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AbstractRuntimeAgentHandlerTest {

    @Test
    void runtimeAgentIsAgentHandlerAndProvidesSingleAgentCardMetadata() {
        AbstractRuntimeAgentHandler agent = new StubRuntimeAgent();

        assertThat(agent).isInstanceOf(AgentHandler.class);
        assertThat(agent.agentId()).isEqualTo("weather-agent");
        assertThat(agent.isHealthy()).isTrue();
        assertThat(agent.agentCard().name()).isEqualTo("Weather Agent");
        assertThat(agent.agentCard().description()).isEqualTo("Answers weather questions.");
        assertThat(agent.agentCard().capabilities().streaming()).isTrue();
        assertThat(agent.agentCard().preferredTransport()).isEqualTo(TransportProtocol.JSONRPC.asString());
        assertThat(agent.agentCard().supportedInterfaces())
                .extracting(AgentInterface::protocolBinding, AgentInterface::url)
                .containsExactly(tuple(TransportProtocol.JSONRPC.asString(), "/a2a"));
    }

    @Test
    void runtimeAgentRejectsBlankIdentityFields() {
        assertThatThrownBy(() -> new InvalidRuntimeAgent())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
    }

    private static final class StubRuntimeAgent extends AbstractRuntimeAgentHandler {

        private StubRuntimeAgent() {
            super("weather-agent", "Weather Agent", "Answers weather questions.");
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of("ok");
        }

        @Override
        public AgentResultAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }

    private static final class InvalidRuntimeAgent extends AbstractRuntimeAgentHandler {

        private InvalidRuntimeAgent() {
            super(" ", "Invalid", "Invalid.");
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.empty();
        }

        @Override
        public AgentResultAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }
}
