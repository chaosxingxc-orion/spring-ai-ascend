package com.huawei.ascend.service.bootstrap;

import com.huawei.ascend.service.access.config.AccessLayerConfiguration;
import com.huawei.ascend.service.access.egress.EgressQueueRegistry;
import com.huawei.ascend.service.access.protocol.a2a.A2aAcceptedResponse;
import com.huawei.ascend.service.access.protocol.a2a.A2aAccessService;
import com.huawei.ascend.service.access.protocol.a2a.A2aEnvelope;
import com.huawei.ascend.service.access.protocol.a2a.A2aOutput;
import com.huawei.ascend.service.access.protocol.a2a.A2aOutputHandle;
import com.huawei.ascend.service.access.protocol.a2a.A2aOutputRegistry;
import com.huawei.ascend.service.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.event.EngineStartedEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineOutput;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.session.config.SessionManageConfiguration;
import com.huawei.ascend.service.taskcontrol.config.TaskControlAutoConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end test from the access layer's perspective: an A2A request enters,
 * flows access → task-centric-control → engine → fake agent, and the reply
 * comes back out through the access layer's A2A output channel.
 *
 * <p>This is the test the human review asked for — it exercises the whole
 * five-layer stack as a single wired runtime (not one module in isolation),
 * proving the glue closes the loop. The agent framework is faked with a small
 * echo {@link AgentHandler} so no external runtime is needed.
 */
@SpringBootTest(classes = AgentServiceEndToEndIT.TestRuntime.class)
class AgentServiceEndToEndIT {

    private static final String TENANT = "tenant-e2e";
    private static final String AGENT = "echo-agent";
    private static final String FAILING_AGENT = "boom-agent";

    @Autowired
    private A2aAccessService a2aAccessService;

    @Autowired
    private A2aOutputRegistry outputRegistry;

    @Autowired
    private EgressQueueRegistry egressQueueRegistry;

    @Test
    void a2aRequestRunsThroughTheStackAndRepliesBack() {
        A2aEnvelope envelope = envelope("session-1", "hello world");

        A2aAcceptedResponse accepted = a2aAccessService.send(envelope);

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.taskId()).isNotBlank();
        assertThat(accepted.tenantId()).isEqualTo(TENANT);

        // task-control dispatch and egress delivery run on their own threads, so
        // poll briefly for the reply to surface on the A2A output channel.
        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-1", accepted.taskId());
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs).anyMatch(o -> "Message".equals(o.kind()));
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> String.valueOf(o.body()).contains("hello world"));

        // The reply channel must be torn down after the terminal frame — no leak.
        awaitEgressCleanup("session-1", accepted.taskId());
        assertThat(egressQueueRegistry.find(TENANT, "session-1", accepted.taskId())).isEmpty();
    }

    private void awaitEgressCleanup(String sessionId, String taskId) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline
                && egressQueueRegistry.find(TENANT, sessionId, taskId).isPresent()) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Test
    void aThrowingAgentStillRepliesWithATerminalError() {
        A2aEnvelope envelope = envelope(FAILING_AGENT, "session-err", "trigger failure");

        A2aAcceptedResponse accepted = a2aAccessService.send(envelope);
        assertThat(accepted.accepted()).isTrue();

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-err", accepted.taskId());
        List<A2aOutput> outputs = awaitOutputs(handle);

        // A handler that throws must still yield a terminal reply — no hang, no leak.
        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> "error".equals(o.kind()));
        awaitEgressCleanup("session-err", accepted.taskId());
        assertThat(egressQueueRegistry.find(TENANT, "session-err", accepted.taskId())).isEmpty();
    }

    private List<A2aOutput> awaitOutputs(A2aOutputHandle handle) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        List<A2aOutput> outputs = outputRegistry.list(handle);
        while (System.nanoTime() < deadline
                && (outputs.isEmpty() || !outputs.get(outputs.size() - 1).terminal())) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outputs = outputRegistry.list(handle);
        }
        return outputs;
    }

    private static A2aEnvelope envelope(String sessionId, String text) {
        return envelope(AGENT, sessionId, text);
    }

    private static A2aEnvelope envelope(String agentId, String sessionId, String text) {
        A2aEnvelope.A2aContext context = new A2aEnvelope.A2aContext(
                TENANT, "user-1", agentId, sessionId, "ctx-1", UUID.randomUUID().toString(), "corr-1");
        A2aEnvelope.A2aMessage message = new A2aEnvelope.A2aMessage(text, List.of(), java.util.Map.of());
        return new A2aEnvelope(context, message, null);
    }

    /**
     * Minimal runtime: the five module configurations plus the bootstrap glue
     * and a fake echo agent. Deliberately avoids the full
     * {@code @SpringBootApplication} so the test does not pull in datasource,
     * Flyway and security auto-configuration it does not need.
     */
    @SpringBootConfiguration
    @Import({
            TaskControlAutoConfiguration.class,
            AgentServiceBootstrapConfiguration.class,
            AccessLayerConfiguration.class,
            SessionManageConfiguration.class,
            EngineAutoConfiguration.class
    })
    static class TestRuntime {

        @Bean
        AgentHandler echoAgentHandler() {
            return new EchoAgentHandler();
        }

        @Bean
        AgentHandler boomAgentHandler() {
            return new ThrowingAgentHandler();
        }
    }

    /** Fake agent framework: echoes the latest user text back as final output. */
    static final class EchoAgentHandler implements AgentHandler {

        @Override
        public String agentId() {
            return AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<EngineExecutionEvent> execute(AgentExecutionContext context) {
            String userText = context.getInput() == null || context.getInput().messages().isEmpty()
                    ? "" : context.getInput().messages().get(0).text();
            EngineStartedEvent started =
                    new EngineStartedEvent(id(), context.getScope(), Instant.now());
            EngineOutputEvent output = new EngineOutputEvent(
                    id(), context.getScope(), Instant.now(), new EngineOutput("echo: " + userText, false));
            EngineCompletedEvent completed = new EngineCompletedEvent(
                    id(), context.getScope(), Instant.now(), new EngineOutput("echo: " + userText, true));
            return Stream.of(started, output, completed);
        }

        private static String id() {
            return UUID.randomUUID().toString();
        }
    }

    /** Fake agent that throws, to prove a failure still yields a terminal reply. */
    static final class ThrowingAgentHandler implements AgentHandler {

        @Override
        public String agentId() {
            return FAILING_AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<EngineExecutionEvent> execute(AgentExecutionContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
