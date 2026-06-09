package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineInput;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentRuntimeProviderTest {

    @Test
    void providerCanRestoreAndExportStateWithoutOwningStore() {
        StubAgent agent = new StubAgent();
        AgentExecutionContext context = context();

        try (Stream<?> results = AgentRuntimeProviders.execute(agent, context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.beforeCalled).isTrue();
        assertThat(agent.afterCalled).isTrue();
        assertThat(context.getAgentState())
                .map(state -> state.get("phase"))
                .contains("exported");
    }

    @Test
    void handlerCanComposeMultipleProvidersWithoutAnotherBaseClass() {
        CompositeAgent agent = new CompositeAgent();
        AgentExecutionContext context = context();

        try (Stream<?> results = AgentRuntimeProviders.execute(agent, context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "sandbox-after", "state-after");
    }

    @Test
    void beforeExecuteFailureCleansUpAlreadyEnteredProviders() {
        BeforeFailureAgent agent = new BeforeFailureAgent();
        AgentExecutionContext context = context();

        assertThatThrownBy(() -> AgentRuntimeProviders.execute(agent, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sandbox denied");

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "state-after");
    }

    private static AgentExecutionContext context() {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task", "agent");
        return new AgentExecutionContext(scope, new EngineInput("text", List.of(), Map.of()));
    }

    private abstract static class TestAgent implements AgentRuntimeHandler {
        private final java.util.ArrayList<AgentRuntimeProvider> providers = new java.util.ArrayList<>();

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

    private static final class StubAgent extends TestAgent {
        private boolean beforeCalled;
        private boolean afterCalled;

        private StubAgent() {
            addProvider(new StateProvider() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    beforeCalled = true;
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    afterCalled = true;
                    context.replaceAgentState(Map.of("phase", "exported"));
                }
            });
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of("ok");
        }
    }

    private static final class CompositeAgent extends TestAgent {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

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
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

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
