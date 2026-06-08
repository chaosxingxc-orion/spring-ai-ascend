package com.huawei.ascend.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies the framework-neutral {@link RuntimeApp} contract: it hands the handler to the
 * {@link RuntimeHost} as {@link RuntimeComponents} and returns the host's {@link RunningRuntime}.
 * This is the Spring-free seam that keeps the runtime core embeddable; the real Spring boot through
 * {@link LocalA2aRuntimeHost} is exercised by the {@code agent-runtime-a2a-llm-e2e} example.
 */
class RuntimeAppTest {

    @Test
    void runHandsHandlerToHostAndReturnsItsRuntime() {
        AgentRuntimeHandler handler = new StubHandler();
        AtomicReference<RuntimeComponents> seen = new AtomicReference<>();
        RunningRuntime started = new RunningRuntime() {
            @Override
            public int port() {
                return 4242;
            }

            @Override
            public void close() {
            }
        };
        RuntimeHost host = components -> {
            seen.set(components);
            return started;
        };

        RunningRuntime result = RuntimeApp.create(handler).run(host);

        assertThat(result).isSameAs(started);
        assertThat(result.port()).isEqualTo(4242);
        assertThat(seen.get().handler()).isSameAs(handler);
    }

    @Test
    void localHostPortZeroOverridesConfiguredServerPort() throws IOException {
        try (ServerSocket configuredPortSocket = new ServerSocket(0)) {
            int configuredPort = configuredPortSocket.getLocalPort();
            LocalA2aRuntimeHost host = new LocalA2aRuntimeHost(0,
                    Map.of("server.port", configuredPort),
                    "--spring.autoconfigure.exclude=" + testAutoConfigurationExcludes());

            try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(host)) {
                assertThat(runtime.port()).isPositive();
                assertThat(runtime.port()).isNotEqualTo(configuredPort);
            }
        }
    }

    private static String testAutoConfigurationExcludes() {
        return String.join(",",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
                "io.github.resilience4j.springboot3.verifier.autoconfigure.SpringBoot3VerifierAutoConfiguration");
    }

    private static final class StubHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "stub";
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
