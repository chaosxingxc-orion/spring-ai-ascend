package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.boot.RuntimeAutoConfiguration;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult.Type;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearing layer B: the handler's hosting contract — agent id, the execute→resultAdapter
 * spine, and Spring bean registration. The echo marker ({@code [echo]}) is an IFF token:
 * it is produced only by the echo strategy, so stripping the prefix turns the OUTPUT
 * assertion red (mutation-RED).
 */
class AlphaRuntimeHandlerTest {

    private static AgentExecutionContext context(String userText) {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task-alpha", "agent-alpha"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user(userText)),
                Map.of());
    }

    private static List<Type> types(List<AgentExecutionResult> results) {
        return results.stream().map(AgentExecutionResult::type).toList();
    }

    @Test
    void agentIdIsAgentAlpha() {
        assertThat(new AlphaRuntimeHandler("agent-alpha").agentId()).isEqualTo("agent-alpha");
    }

    @Test
    void echoesUserTextAsOutputThenCompleted() {
        AlphaRuntimeHandler handler = new AlphaRuntimeHandler("agent-alpha");
        try (Stream<?> raw = handler.execute(context("ping"))) {
            List<AgentExecutionResult> results = handler.resultAdapter().adapt(raw).toList();
            assertThat(types(results)).containsExactly(Type.OUTPUT, Type.COMPLETED);
            // OUTPUT chunk carries the echo marker (IFF token — strip the prefix and this fails)
            assertThat(results.get(0).outputContent()).isEqualTo(AlphaRuntimeHandler.ECHO_PREFIX + "ping");
            assertThat(results.get(0).outputContent()).contains("[echo]");
            // terminal carries the user text back
            assertThat(results.get(1).outputContent()).contains("ping");
        }
    }

    @Test
    void echoesEmptyInputWithoutBreaking() {
        AlphaRuntimeHandler handler = new AlphaRuntimeHandler("agent-alpha");
        try (Stream<?> raw = handler.execute(context(""))) {
            List<AgentExecutionResult> results = handler.resultAdapter().adapt(raw).toList();
            assertThat(types(results)).containsExactly(Type.OUTPUT, Type.COMPLETED);
            assertThat(results.get(1).type()).isEqualTo(Type.COMPLETED);
        }
    }

    @Test
    void resultAdapterCastsAlreadyAdaptedResults() {
        AlphaRuntimeHandler handler = new AlphaRuntimeHandler("agent-alpha");
        List<AgentExecutionResult> results = handler.resultAdapter()
                .adapt(Stream.of(AgentExecutionResult.completed("x"), AgentExecutionResult.output("y")))
                .toList();
        assertThat(results).extracting(AgentExecutionResult::type).containsExactly(Type.COMPLETED, Type.OUTPUT);
        assertThat(results).extracting(AgentExecutionResult::outputContent).containsExactly("x", "y");
    }

    @Test
    void isHealthyByDefault() {
        assertThat(new AlphaRuntimeHandler("agent-alpha").isHealthy()).isTrue();
    }

    @Test
    void registersAsSoleHandlerBeanWithAgentAlphaId() {
        new ApplicationContextRunner()
                .withBean("alphaHandler", AgentRuntimeHandler.class,
                        () -> new AlphaRuntimeHandler("agent-alpha"))
                .withUserConfiguration(RuntimeAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AgentRuntimeHandler.class);
                    assertThat(ctx.getBean(AgentRuntimeHandler.class).agentId()).isEqualTo("agent-alpha");
                });
    }
}
