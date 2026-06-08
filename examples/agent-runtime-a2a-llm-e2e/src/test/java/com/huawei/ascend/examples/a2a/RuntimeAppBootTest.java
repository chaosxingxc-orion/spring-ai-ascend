package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

/**
 * Real boot of the pure-Java {@link RuntimeApp} entry through {@link LocalA2aRuntimeHost} on an
 * ephemeral port with a trivial handler (no LLM, no mocks): the whole five-layer Spring context
 * wires and the A2A access layer serves — verified through the same A2A SDK ({@code A2ACardResolver})
 * the example uses. Lives in the example module: its classpath is DB-free, so the host boots without
 * external infrastructure. (The execution path itself is covered by the real-LLM
 * {@code OpenJiuwenReactAgentA2aE2eTest}.)
 */
class RuntimeAppBootTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void runtimeAppBootsAndServesA2aThroughLocalHost() throws Exception {
        try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(LocalA2aRuntimeHost.port(0))) {
            assertThat(runtime.port()).isGreaterThan(0);

            SampleA2aClient client = new SampleA2aClient(
                    URI.create("http://localhost:" + runtime.port()), TIMEOUT);

            AgentCard card = client.agentCard();
            assertThat(card.capabilities().streaming()).isTrue();
        }
    }

    /** Minimal framework-neutral handler: enough to wire the registry; not exercised by this smoke. */
    private static final class StubHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "smoke-agent";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(raw -> AgentExecutionResult.completed("ok"));
        }
    }
}
