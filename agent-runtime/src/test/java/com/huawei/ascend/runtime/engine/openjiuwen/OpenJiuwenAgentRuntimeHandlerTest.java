package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.EngineInput;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeProviders;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
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
            public Stream<?> execute(AgentExecutionContext context) {
                BaseAgent agent = new EchoBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, openJiuwenConversationId(context), null));
            }
        };

        List<?> rawResults;
        try (Stream<?> stream = AgentRuntimeProviders.execute(handler, context())) {
            rawResults = stream.toList();
        }
        var results = handler.resultAdapter().adapt(rawResults.stream()).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).output().getContent()).isEqualTo("echo: ping");
    }

    @Test
    void openJiuwenCheckpointerRestoresByConversationId() {
        Runner.release("task-1");
        OpenJiuwenAgentRuntimeHandler handler = new OpenJiuwenAgentRuntimeHandler("stateful-agent") {
            @Override
            public Stream<?> execute(AgentExecutionContext context) {
                BaseAgent agent = new StatefulBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, openJiuwenConversationId(context), null));
            }
        };
        AgentExecutionContext first = context("stateful-agent");
        try (Stream<?> rawResults = AgentRuntimeProviders.execute(handler, first)) {
            rawResults.toList();
        }

        try (Stream<?> rawResults = AgentRuntimeProviders.execute(handler, first)) {
            var mapped = handler.resultAdapter().adapt(rawResults).toList();
            assertThat(mapped).hasSize(1);
            assertThat(mapped.get(0).output().getContent()).isEqualTo("turn: 2");
        } finally {
            Runner.release("task-1");
        }
    }

    @Test
    void openJiuwenCheckpointerCanBeSetDirectlyByRuntimeConfiguration() {
        Checkpointer previous = CheckpointerFactory.getCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        OpenJiuwenAgentRuntimeHandler handler = new OpenJiuwenAgentRuntimeHandler("stateful-agent") {
            @Override
            public Stream<?> execute(AgentExecutionContext context) {
                BaseAgent agent = new StatefulBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, openJiuwenConversationId(context), null));
            }
        };
        AgentExecutionContext context = context("stateful-agent");
        try {
            CheckpointerFactory.getCheckpointer().release("task-1");
            try (Stream<?> rawResults = AgentRuntimeProviders.execute(handler, context)) {
                rawResults.toList();
            }

            try (Stream<?> rawResults = AgentRuntimeProviders.execute(handler, context)) {
                var mapped = handler.resultAdapter().adapt(rawResults).toList();
                assertThat(mapped).hasSize(1);
                assertThat(mapped.get(0).output().getContent()).isEqualTo("turn: 2");
            }
        } finally {
            CheckpointerFactory.getCheckpointer().release("task-1");
            CheckpointerFactory.setDefaultCheckpointer(previous);
        }
    }

    @Test
    void openJiuwenRedisCheckpointerCanBeInstantiatedByUrlConfiguration() {
        Checkpointer checkpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", "redis://localhost:6379")));

        assertThat(checkpointer).isInstanceOf(RedisCheckpointer.class);
    }

    private static AgentExecutionContext context() {
        return context("base-agent");
    }

    private static AgentExecutionContext context(String agentId) {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task-1", agentId);
        EngineInput input = new EngineInput("text", List.of(Message.user("ping")), Map.of());
        return new AgentExecutionContext(scope, input);
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
