package com.huawei.ascend.a2a.memory.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Real A2A over-the-wire shared memory: boots the no-LLM
 * {@link DeterministicSharedMemoryAgent} on a random port and drives it over the
 * actual A2A JSON-RPC wire. Two messages on the SAME A2A contextId share the
 * blackboard; ownership and tenant/context isolation hold over the wire. No API key.
 */
class A2aSharedMemoryWireTest {

    private static ConfigurableApplicationContext boot() {
        return new SpringApplicationBuilder(DeterministicSharedMemoryAgent.class)
                .run("--server.port=0", "--spring.main.web-application-type=servlet",
                        "--logging.level.root=WARN");
    }

    @Test
    void agentsShareTheBlackboardOverTheA2aWire() {
        try (ConfigurableApplicationContext ctx = boot()) {
            Integer port = ctx.getEnvironment().getProperty("local.server.port", Integer.class);
            assertNotNull(port, "server port available");
            Wire wire = new Wire("http://localhost:" + port, "demo-tenant");

            String contextA = "collab-A";
            // risk-agent writes its conclusion on collaboration A...
            assertTrue(wire.send(contextA, "PUT risk-agent riskAssessment C3").startsWith("OK"),
                    "write accepted over the wire");
            // ...a later A2A call on the SAME contextId reads it back — real shared memory over the wire.
            assertEquals("C3", wire.send(contextA, "GET riskAssessment"),
                    "second agent reads risk-agent's conclusion via the A2A contextId");
            // a non-owner write is rejected by the engine, over the wire.
            assertEquals("DENIED", wire.send(contextA, "PUT intruder riskAssessment tampered"),
                    "ownership enforced over the wire");
            // a different collaboration (contextId) is isolated.
            assertEquals("MISS", wire.send("collab-OTHER", "GET riskAssessment"),
                    "another A2A collaboration shares nothing");
        }
    }

    /** Minimal A2A blocking client: resolves the card, posts message/send, returns the reply text. */
    private static final class Wire {
        private final ClientTransport transport;
        private final String tenant;

        Wire(String baseUrl, String tenant) {
            this.tenant = tenant;
            try {
                AgentCard card = A2ACardResolver.builder().baseUrl(baseUrl).build().getAgentCard();
                this.transport = new JSONRPCTransport(card);
            } catch (Exception e) {
                throw new IllegalStateException("resolve card failed: " + e.getMessage(), e);
            }
        }

        String send(String contextId, String text) {
            try {
                Message message = Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .messageId(UUID.randomUUID().toString())
                        .contextId(contextId)
                        .parts(List.<Part<?>>of(new TextPart(text)))
                        .build();
                MessageSendParams params = MessageSendParams.builder().message(message).build();
                // tenant rides the header (NOT params.tenant(), which would route to /a2a/{tenant})
                ClientCallContext cc = new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", tenant));
                return textOf(transport.sendMessage(params, cc));
            } catch (Exception e) {
                throw new IllegalStateException("send failed: " + e.getMessage(), e);
            }
        }

        private static String textOf(EventKind result) {
            StringBuilder sb = new StringBuilder();
            if (result instanceof Message m) {
                appendParts(sb, m.parts());
            } else if (result instanceof Task t) {
                // The answer is the artifact (from output()); the terminal status may
                // echo the same text — read artifacts first, fall back to status, never both.
                if (t.artifacts() != null && !t.artifacts().isEmpty()) {
                    for (Artifact a : t.artifacts()) {
                        appendParts(sb, a.parts());
                    }
                } else if (t.status() != null && t.status().message() != null) {
                    appendParts(sb, t.status().message().parts());
                }
            }
            return sb.toString().trim();
        }

        private static void appendParts(StringBuilder sb, List<Part<?>> parts) {
            if (parts == null) {
                return;
            }
            for (Part<?> p : parts) {
                if (p instanceof TextPart tp) {
                    sb.append(tp.text());
                }
            }
        }
    }
}
