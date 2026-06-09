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

class AbstractStatefulAgentRuntimeHandlerTest {

    @Test
    void executeRunsRestoreHookBodyAndExportHookWithoutOwningStore() {
        StubStatefulAgent agent = new StubStatefulAgent();
        AgentExecutionContext context = context();

        try (Stream<?> results = AgentRuntimeExtensions.execute(agent, context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.beforeCalled).isTrue();
        assertThat(agent.afterCalled).isTrue();
        assertThat(context.getAgentState())
                .map(snapshot -> snapshot.values().get("phase"))
                .contains("exported");
    }

    @Test
    void handlerCanComposeMultipleExtensionsWithoutAnotherBaseClass() {
        CompositeAgent agent = new CompositeAgent();
        AgentExecutionContext context = context();

        try (Stream<?> results = AgentRuntimeExtensions.execute(agent, context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "sandbox-after", "state-after");
    }

    @Test
    void beforeExecuteFailureCleansUpAlreadyEnteredExtensions() {
        BeforeFailureAgent agent = new BeforeFailureAgent();
        AgentExecutionContext context = context();

        assertThatThrownBy(() -> AgentRuntimeExtensions.execute(agent, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sandbox denied");

        assertThat(agent.events).containsExactly("state-before", "sandbox-before", "state-after");
    }

    private static AgentExecutionContext context() {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task", "agent");
        return new AgentExecutionContext(scope, new EngineInput("text", List.of(), Map.of()));
    }

    private static final class StubStatefulAgent extends AbstractStatefulAgentRuntimeHandler {
        private boolean beforeCalled;
        private boolean afterCalled;

        private StubStatefulAgent() {
            super("agent", "Stateful Agent", "Stateful test agent.");
        }

        @Override
        protected void beforeExecute(AgentExecutionContext context) {
            beforeCalled = true;
        }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context) {
            return Stream.of("ok");
        }

        @Override
        protected void afterExecute(AgentExecutionContext context) {
            afterCalled = true;
            context.replaceAgentState(Map.of("phase", "exported"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }

    private static final class CompositeAgent extends AbstractAgentRuntimeHandler {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

        private CompositeAgent() {
            super("agent", "Composite Agent", "Composite test agent.");
            addRuntimeExtension(new AgentRuntimeExtension() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("state-before");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("state-after");
                }
            });
            addRuntimeExtension(new AgentRuntimeExtension() {
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

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }

    private static final class BeforeFailureAgent extends AbstractAgentRuntimeHandler {
        private final java.util.ArrayList<String> events = new java.util.ArrayList<>();

        private BeforeFailureAgent() {
            super("agent", "Before Failure Agent", "Before failure test agent.");
            addRuntimeExtension(new AgentRuntimeExtension() {
                @Override
                public void beforeExecute(AgentExecutionContext context) {
                    events.add("state-before");
                }

                @Override
                public void afterExecute(AgentExecutionContext context) {
                    events.add("state-after");
                }
            });
            addRuntimeExtension(new AgentRuntimeExtension() {
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

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> Stream.empty();
        }
    }
}
