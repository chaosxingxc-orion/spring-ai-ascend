package com.huawei.ascend.samples.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.service.engine.adapter.openjiuwen.OpenJiuwenAgentHandler;
import com.huawei.ascend.service.engine.adapter.openjiuwen.OpenJiuwenMessageConverter;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.event.EngineStartedEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import com.huawei.ascend.service.engine.model.EngineMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: drives a real ping through the engine's openJiuwen handler against
 * the configured LLM endpoint. Tagged {@code smoke} and skipped (not failed)
 * when no endpoint is configured, so it never breaks builds without access.
 *
 * <p>Named {@code ...SmokeTest} (not {@code ...IT}) so the default surefire
 * {@code test} phase runs it — the sample module does not activate failsafe.
 * Run with: export OJW_API_KEY=... OJW_API_BASE=... ; mvn test.
 */
@Tag("smoke")
class OpenJiuwenEchoAgentSmokeTest {

    @Test
    void echoAgent_overEngine_completesAgainstRealLlm() {
        String apiKey = System.getenv("OJW_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "OJW_API_KEY not set — skipping real-LLM smoke test");

        OpenJiuwenAgentHandler handler = new OpenJiuwenAgentHandler(
                "echo-agent", new EchoOpenJiuwenAgentFactory(), new OpenJiuwenMessageConverter());

        EngineExecutionScope scope = new EngineExecutionScope("t", "u", "s", "smoke-1", "echo-agent");
        EngineInput input = new EngineInput("text", List.of(new EngineMessage("user", "ping")), Map.of());

        List<EngineExecutionEvent> events = handler.execute(new AgentExecutionContext(scope, input)).toList();

        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        assertThat(events.get(0)).isInstanceOf(EngineStartedEvent.class);
        assertThat(events.get(events.size() - 1)).isInstanceOf(EngineCompletedEvent.class);
        EngineCompletedEvent done = (EngineCompletedEvent) events.get(events.size() - 1);
        assertThat(done.getFinalOutput().getContent()).isNotBlank();
    }
}
