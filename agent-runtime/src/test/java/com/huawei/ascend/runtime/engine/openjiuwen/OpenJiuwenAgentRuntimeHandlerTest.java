package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineInput;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeExtensions;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OpenJiuwenAgentRuntimeHandlerTest {

    @Test
    void subclassReturnsRawOpenJiuwenResultAndAdapterMapsIt() {
        OpenJiuwenAgentRuntimeHandler handler = new OpenJiuwenAgentRuntimeHandler("base-agent") {
            @Override
            protected Stream<?> doExecute(AgentExecutionContext context) {
                BaseAgent agent = new EchoBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, openJiuwenSession(context, agent), null));
            }
        };

        List<?> rawResults;
        try (Stream<?> stream = AgentRuntimeExtensions.execute(handler, context())) {
            rawResults = stream.toList();
        }
        var results = handler.resultAdapter().adapt(rawResults.stream()).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).output().getContent()).isEqualTo("echo: ping");
    }

    @Test
    void openJiuwenSessionStateFlowsThroughRuntimeAgentState() {
        OpenJiuwenAgentRuntimeHandler handler = new OpenJiuwenAgentRuntimeHandler("stateful-agent") {
            @Override
            protected Stream<?> doExecute(AgentExecutionContext context) {
                BaseAgent agent = new StatefulBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, openJiuwenSession(context, agent), null));
            }
        };
        AgentExecutionContext first = context("stateful-agent");
        try (Stream<?> rawResults = AgentRuntimeExtensions.execute(handler, first)) {
            rawResults.toList();
        }

        AgentExecutionContext second = new AgentExecutionContext(first.getScope(), first.getInput(),
                first.getAgentState().orElseThrow());
        try (Stream<?> rawResults = AgentRuntimeExtensions.execute(handler, second)) {
            rawResults.toList();
        }

        Map<String, Object> openJiuwenState = openJiuwenState(second);
        assertThat(openJiuwenGlobalState(openJiuwenState)).containsEntry("turn", 2);
        assertThat(second.getAgentState().orElseThrow().values())
                .containsEntry(OpenJiuwenAgentRuntimeHandler.STATE_SESSION_ID, "task-1");
    }

    private static AgentExecutionContext context() {
        return context("base-agent");
    }

    private static AgentExecutionContext context(String agentId) {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task-1", agentId);
        EngineInput input = new EngineInput("text", List.of(Message.user("ping")), Map.of());
        return new AgentExecutionContext(scope, input);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> openJiuwenState(AgentExecutionContext context) {
        return (Map<String, Object>) context.getAgentState()
                .orElseThrow()
                .values()
                .get(OpenJiuwenAgentRuntimeHandler.STATE_VALUES);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> openJiuwenGlobalState(Map<String, Object> openJiuwenState) {
        return (Map<String, Object>) openJiuwenState.get("global_state");
    }

    public static final class EchoBaseAgent extends BaseAgent {
        private Object config;

        private EchoBaseAgent() {
            super(AgentCard.builder()
                    .id("base-agent")
                    .name("base-agent")
                    .description("base agent")
                    .build());
        }

        @Override
        public BaseAgent configure(Object config) {
            this.config = config;
            return this;
        }

        @Override
        public Object getConfig() {
            return config;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            Object query = inputs instanceof Map<?, ?> map ? map.get("query") : inputs;
            return Map.of("result_type", "answer", "output", "echo: " + query);
        }

        public Object invoke(Object inputs, AgentSessionApi session) {
            return invoke(inputs, (Session) session);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }

    public static final class StatefulBaseAgent extends BaseAgent {
        private Object config;

        private StatefulBaseAgent() {
            super(AgentCard.builder()
                    .id("stateful-agent")
                    .name("stateful-agent")
                    .description("stateful agent")
                    .build());
        }

        @Override
        public BaseAgent configure(Object config) {
            this.config = config;
            return this;
        }

        @Override
        public Object getConfig() {
            return config;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            Object previous = session.getState("turn");
            int nextTurn = previous instanceof Number number ? number.intValue() + 1 : 1;
            session.updateState(Map.of("turn", nextTurn));
            return Map.of("result_type", "answer", "output", "turn: " + nextTurn);
        }

        public Object invoke(Object inputs, AgentSessionApi session) {
            return invoke(inputs, (Session) session);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }
}
