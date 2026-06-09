package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentRuntimeProviderChainTest {

    @Test
    void handlerCanComposeMultipleProvidersWithoutAnotherBaseClass() {
        CompositeAgent agent = new CompositeAgent();
        AgentExecutionContext context = context();

        try (Stream<?> results = AgentRuntimeProviderChain.execute(agent, context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "sandbox-after", "state-after");
    }

    @Test
    void beforeExecuteFailureCleansUpAlreadyEnteredProviders() {
        BeforeFailureAgent agent = new BeforeFailureAgent();
        AgentExecutionContext context = context();

        assertThatThrownBy(() -> AgentRuntimeProviderChain.execute(agent, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sandbox denied");

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "state-after");
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(), Map.of());
    }

    private abstract static class TestAgent implements AgentRuntimeHandler {
        private final ArrayList<AgentRuntimeProvider> providers = new ArrayList<>();

        @Override
        public String agentId() {
            return "agent";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public List<AgentRuntimeProvider> providers() {
            return List.copyOf(providers);
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }

        protected final void addProvider(AgentRuntimeProvider provider) {
            providers.add(provider);
        }
    }

    private static final class CompositeAgent extends TestAgent {
        private final ArrayList<String> events = new ArrayList<>();

        private CompositeAgent() {
            addProvider(new StateProvider() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("state-before");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("state-after");
                }
            });
            addProvider(new AgentRuntimeProvider() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("sandbox-before");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("sandbox-after");
                }
            });
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of("ok");
        }
    }

    private static final class BeforeFailureAgent extends TestAgent {
        private final ArrayList<String> events = new ArrayList<>();

        private BeforeFailureAgent() {
            addProvider(new StateProvider() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("state-before");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("state-after");
                }
            });
            addProvider(new AgentRuntimeProvider() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("sandbox-before");
                    throw new IllegalStateException("sandbox denied");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("sandbox-after");
                }
            });
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of("should-not-run");
        }
    }
}
