package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AbstractAgentRuntimeHandlerTest {

    @Test
    void runtimeAgentIsAgentHandlerAndProvidesSingleAgentCardMetadata() {
        AbstractAgentRuntimeHandler agent = new StubRuntimeAgent();

        assertThat(agent).isInstanceOf(AgentRuntimeHandler.class);
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

    private static final class StubRuntimeAgent extends AbstractAgentRuntimeHandler {

        private StubRuntimeAgent() {
            super("weather-agent", "Weather Agent", "Answers weather questions.");
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
            super(" ", "Invalid", "Invalid.");
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
