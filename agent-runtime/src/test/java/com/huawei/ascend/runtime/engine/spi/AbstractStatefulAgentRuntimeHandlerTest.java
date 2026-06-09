package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

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

        try (Stream<?> results = agent.execute(context)) {
            assertThat(results.toList()).isEqualTo(List.of("ok"));
        }

        assertThat(agent.beforeCalled).isTrue();
        assertThat(agent.afterCalled).isTrue();
        assertThat(context.getAgentState())
                .map(snapshot -> snapshot.values().get("phase"))
                .contains("exported");
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
}
