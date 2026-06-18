package com.huawei.ascend.examples.a2a.remoteagenttool;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Interactive console client for manual end-to-end verification.
 * Connects to an agent-runtime instance via A2A JSON-RPC and streams responses.
 * Automatically resumes tasks that enter INPUT_REQUIRED state.
 *
 * <h3>Usage</h3>
 * <pre>
 * mvn -f examples/agent-runtime-a2a-remote-agent-tool-e2e/pom.xml exec:java \
 *   -Dexec.mainClass=com.huawei.ascend.examples.a2a.remoteagenttool.A2aConsoleClientApplication \
 *   -Dexec.args="http://localhost:18081 local-openjiuwen manual-user"
 * </pre>
 * Env vars: {@code SAA_SAMPLE_A2A_BASE_URL}, {@code SAA_SAMPLE_AGENT_ID}, {@code SAA_SAMPLE_USER_ID}.
 */
public final class A2aConsoleClientApplication {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String DEFAULT_AGENT_ID = "local-openjiuwen";
    private static final String DEFAULT_USER_ID = "manual-user";

    private A2aConsoleClientApplication() {
    }

    public static void main(String[] args) throws Exception {
        URI baseUri = URI.create(value(args, 0, "SAA_SAMPLE_A2A_BASE_URL", DEFAULT_BASE_URL));
        String agentId = value(args, 1, "SAA_SAMPLE_AGENT_ID", DEFAULT_AGENT_ID);
        String userId = value(args, 2, "SAA_SAMPLE_USER_ID", DEFAULT_USER_ID);
        String sessionId = "manual-session-" + UUID.randomUUID();
        A2aStreamingTestClient client = new A2aStreamingTestClient(baseUri, TIMEOUT);

        System.out.println("Connected to " + client.agentCard().name() + " at " + baseUri);
        System.out.println("Type a message and press Enter. Type exit to quit.");
        String currentTaskId = null;
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String input = scanner.nextLine().trim();
                if (input.isBlank()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    return;
                }
                List<StreamingEventKind> events = client.streamMessage(
                        userId, agentId, sessionId, currentTaskId, input);

                if (A2aStreamingTestClient.isInputRequired(events)) {
                    currentTaskId = A2aStreamingTestClient.firstTaskId(events);
                    System.out.println("[agent needs more input - type your follow-up]");
                } else {
                    currentTaskId = null;
                }
                String answer = A2aStreamingTestClient.textFrom(events);
                System.out.println(answer.isBlank() ? "(empty response)" : answer);
            }
        }
    }

    private static String value(String[] args, int index, String envName, String defaultValue) {
        if (args.length > index && !args[index].isBlank()) {
            return args[index];
        }
        String envValue = System.getenv(envName);
        return envValue == null || envValue.isBlank() ? defaultValue : envValue;
    }
}
