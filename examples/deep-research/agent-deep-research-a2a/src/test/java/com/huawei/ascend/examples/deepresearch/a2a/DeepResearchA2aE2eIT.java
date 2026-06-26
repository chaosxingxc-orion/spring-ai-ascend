/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * End-to-end tests against live stub sub-agents, mem0, and optional real LLM.
 *
 * <p>Enable with {@code DEEP_RESEARCH_E2E_ENABLED=true} after starting stub jars via
 * {@code start-stubs.sh}. Requires {@code DEEP_RESEARCH_LLM_API_KEY} for streaming branches.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DeepResearchApplication.class)
class DeepResearchA2aE2eIT {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration RECALL_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENT_ID = DeepResearchConstants.AGENT_ID;

    @LocalServerPort
    private int port;

    @Test
    void round1ProducesCitedComparisonReport() throws Exception {
        assumeE2eEnabled();
        assumeTrue(hasRealLlmKey(), "DEEP_RESEARCH_LLM_API_KEY not set for live LLM E2E");
        assumeTrue(stubsReachable(), "Stub sub-agents on 13004-13006 are not reachable");

        DeepResearchA2aClient client = client(STREAM_TIMEOUT);
        AgentCard agentCard = client.agentCard();
        assertThat(agentCard.name()).isEqualTo(AGENT_ID);

        String userId = "e2e-user-" + UUID.randomUUID();
        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage(
                userId, AGENT_ID, sessionId, DeepResearchFixtureTexts.round1Question());

        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(DeepResearchA2aClient.isTerminal(event)).isTrue());

        String answer = DeepResearchA2aClient.textFrom(events);
        assertThat(answer).isNotBlank();
        assertThat(answer.toLowerCase(Locale.ROOT))
                .containsAnyOf("comparison_table", "report", "对比", "火山", "deepseek", "智谱");
    }

    @Test
    void round2RecallsPriorConclusionFromMem0() throws Exception {
        assumeE2eEnabled();
        assumeTrue(hasRealLlmKey(), "DEEP_RESEARCH_LLM_API_KEY not set for live LLM E2E");
        assumeTrue(DeepResearchMem0Support.isReachable(mem0BaseUrl()),
                "Mem0 endpoint not reachable: " + mem0BaseUrl());

        String tenantId = System.getenv().getOrDefault("DEEP_RESEARCH_TENANT_ID", "deep-research-tenant");
        String userId = "e2e-recall-" + UUID.randomUUID();
        seedPriorComparisonConclusion(tenantId, userId);

        DeepResearchA2aClient client = client(RECALL_TIMEOUT);
        String sessionId = "session-recall-" + UUID.randomUUID();
        long started = System.nanoTime();
        List<StreamingEventKind> events = client.streamMessage(
                userId, AGENT_ID, sessionId, DeepResearchFixtureTexts.round2Question());
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMs).isLessThan(RECALL_TIMEOUT.toMillis());
        assertThat(events).anySatisfy(event -> assertThat(DeepResearchA2aClient.isTerminal(event)).isTrue());

        String answer = DeepResearchA2aClient.textFrom(events).toLowerCase(Locale.ROOT);
        assertThat(answer).containsAnyOf("deepseek", "128k", "128");
    }

    private DeepResearchA2aClient client(Duration timeout) {
        return new DeepResearchA2aClient(URI.create("http://localhost:" + port), timeout);
    }

    private static void seedPriorComparisonConclusion(String tenantId, String userId) {
        DeepResearchMem0MemoryProvider provider = new DeepResearchMem0MemoryProvider(
                mem0BaseUrl(), mem0ApiKey(), false);
        AgentExecutionContext context = new AgentExecutionContext(
                new RuntimeIdentity(tenantId, userId, "seed-session", "seed-task", AGENT_ID),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user(DeepResearchFixtureTexts.round1Question())),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "seed-conversation"));
        provider.init(context);
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(
                "seed-" + UUID.randomUUID(),
                "assistant",
                "对比结论：上下文窗口最大的是 DeepSeek 128K",
                Map.of())));
    }

    private static void assumeE2eEnabled() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("DEEP_RESEARCH_E2E_ENABLED")),
                "Set DEEP_RESEARCH_E2E_ENABLED=true and start stub sub-agents on 13004-13006");
    }

    private static boolean hasRealLlmKey() {
        String apiKey = System.getenv("DEEP_RESEARCH_LLM_API_KEY");
        return apiKey != null && !apiKey.isBlank() && !"test-key".equals(apiKey);
    }

    private static boolean stubsReachable() {
        return reachable(stubUrl("DEEP_RESEARCH_SEARCH_A2A_URL", "http://localhost:13004"))
                && reachable(stubUrl("DEEP_RESEARCH_READ_A2A_URL", "http://localhost:13005"))
                && reachable(stubUrl("DEEP_RESEARCH_VERIFY_A2A_URL", "http://localhost:13006"));
    }

    private static String stubUrl(String envName, String defaultUrl) {
        String value = System.getenv(envName);
        return value == null || value.isBlank() ? defaultUrl : value;
    }

    private static boolean reachable(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalized + "/.well-known/agent-card.json"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String mem0BaseUrl() {
        return System.getenv().getOrDefault("DEEP_RESEARCH_MEM0_BASE_URL", "http://7.209.189.82:8000");
    }

    private static String mem0ApiKey() {
        String value = System.getenv("DEEP_RESEARCH_MEM0_API_KEY");
        return value == null ? "" : value;
    }
}
