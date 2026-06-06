package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;
import com.huawei.ascend.runtime.schema.Message;
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
            public Stream<?> execute(AgentExecutionContext context) {
                BaseAgent agent = new EchoBaseAgent();
                Object input = toOpenJiuwenInput(context);
                return Stream.of(Runner.runAgent(agent, input, null, null));
            }
        };

        List<?> rawResults = handler.execute(context()).toList();
        var results = handler.resultAdapter().adapt(rawResults.stream()).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).output().getContent()).isEqualTo("echo: ping");
    }

    private static AgentExecutionContext context() {
        EngineExecutionScope scope = new EngineExecutionScope("tenant", "user", "session", "task-1", "base-agent");
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
}
