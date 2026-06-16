package com.huawei.ascend.collab.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.huawei.ascend.collab.a2a.A2aWorker;
import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.Coordinator;
import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Real A2A round-trip: boots the no-LLM {@link DeterministicEchoAgent} on a random
 * port and drives {@link A2aWorker} against it over the actual A2A JSON-RPC wire —
 * proving the engine→A2A bridge works end to end, deterministically, no API key.
 * Whole a2a-sdk stack aligned to 1.0.0.Final (matching the platform).
 *
 * <p><b>Token echo over A2A.</b> The coordinator's token check requires the remote to
 * echo the issued token on its response metadata. A runtime-hosted agent cannot do that
 * today: {@code AgentExecutionResult} carries only text, and the runtime emits a text
 * artifact (no response-metadata channel), so {@code DeterministicEchoAgent} does NOT echo
 * the token. The real-wire test therefore proves the <i>rejection</i> path (a non-echoing
 * agent is rejected — the security fix); the compliant-agent <i>accept</i> path is covered
 * offline in {@code A2aWorkerTokenEchoTest} with a transport that echoes. Completing a
 * token-validated task with a runtime agent over the wire needs a platform SPI to echo
 * response metadata (follow-up, not in this module).
 */
class A2aWorkerE2eTest {

    private static ConfigurableApplicationContext boot() {
        return new SpringApplicationBuilder(DeterministicEchoAgent.class)
                .run("--server.port=0", "--spring.main.web-application-type=servlet",
                        "--logging.level.root=WARN");
    }

    private static String baseUrl(ConfigurableApplicationContext ctx) {
        Integer port = ctx.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port, "local.server.port available");
        return "http://localhost:" + port; // agent base; card resolved from /.well-known/agent-card.json
    }

    @Test
    void a2aWorkerReachesRealAgentOverTheWire() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));

            TaskToken token = TaskToken.issue("t1", "echo", "echo-worker", "demo-tenant",
                    UUID.randomUUID(), 30_000, System.currentTimeMillis());
            WorkResult r = worker.execute(SubTask.of("t1", "echo", "hello world"), token);

            // Transport / card / JSON-RPC round-trip works: the worker reaches the agent and
            // maps its terminal Task to COMPLETED with output.
            assertEquals(WorkResult.Status.COMPLETED, r.status(),
                    "remote echo agent completes; detail=" + r.detail() + " output=" + r.output());
            assertNotNull(r.output(), "output present");
            // The runtime agent has no way to echo the token (text-only response), so the
            // worker parses no echoed token — which is why the coordinator rejects it below.
            assertNull(r.echoedToken(), "runtime agent cannot echo token metadata -> no echo parsed");
        }
    }

    @Test
    void coordinatorRejectsRealAgentThatDoesNotEchoToken() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));
            Coordinator coordinator = new Coordinator(List.of(worker));

            CollaborationResult result = coordinator.run(List.of(
                    SubTask.of("t1", "echo", "task one"),
                    SubTask.of("t2", "echo", "task two")));

            // Token validation is now enforced on the real bridge: an agent that does not
            // echo the issued token is rejected (no longer auto-trusted). See class doc.
            assertEquals(WorkResult.Status.REJECTED, result.outcomes().get("t1"),
                    "non-echoing real agent rejected: " + result.outcomes());
            assertEquals(WorkResult.Status.REJECTED, result.outcomes().get("t2"),
                    "non-echoing real agent rejected: " + result.outcomes());
        }
    }
}
