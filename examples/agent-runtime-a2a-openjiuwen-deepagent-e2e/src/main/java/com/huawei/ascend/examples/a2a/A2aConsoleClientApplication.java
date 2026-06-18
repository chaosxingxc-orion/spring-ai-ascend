package com.huawei.ascend.examples.a2a;

import java.net.URI;
import java.time.Duration;
import java.util.Scanner;
import java.util.UUID;

public final class A2aConsoleClientApplication {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String DEFAULT_AGENT_ID = "openjiuwen-deep-agent";
    private static final String DEFAULT_USER_ID = "manual-user";

    private A2aConsoleClientApplication() {
    }

    public static void main(String[] args) throws Exception {
        URI baseUri = URI.create(value(args, 0, "SAA_SAMPLE_A2A_BASE_URL", DEFAULT_BASE_URL));
        String agentId = value(args, 1, "SAA_SAMPLE_AGENT_ID", DEFAULT_AGENT_ID);
        String userId = value(args, 2, "SAA_SAMPLE_USER_ID", DEFAULT_USER_ID);
        String sessionId = "manual-session-" + UUID.randomUUID();
        SampleA2aClient client = new SampleA2aClient(baseUri, TIMEOUT);

        System.out.println("Connected to " + client.agentCard().name() + " at " + baseUri);
        System.out.println("Type a message and press Enter. Type exit to quit.");
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
                String answer = SampleA2aClient.textFrom(client.streamMessage(userId, agentId, sessionId, input));
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
