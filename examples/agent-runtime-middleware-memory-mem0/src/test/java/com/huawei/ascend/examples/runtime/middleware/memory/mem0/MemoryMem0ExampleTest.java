package com.huawei.ascend.examples.runtime.middleware.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("manual")
class MemoryMem0ExampleTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    void mem0RestMemoryProviderWorksThroughOpenJiuwenHandlerExecution() throws Exception {
        String baseUrl = System.getenv("SAA_SAMPLE_MEM0_BASE_URL");
        assumeTrue(hasText(baseUrl), "Set SAA_SAMPLE_MEM0_BASE_URL to run the real Mem0 example");
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "Set SAA_SAMPLE_LLM_API_KEY to run the real LLM example");
        Mem0RestMemoryProvider memoryProvider = new Mem0RestMemoryProvider(
                baseUrl, System.getenv("SAA_SAMPLE_MEM0_API_KEY"), false, envOrDefault("SAA_SAMPLE_MEM0_API_MODE", "oss"));
        AgentExecutionContext greenTeaUserContext = MiddlewareTestFixtures.context("mem0-state-" + System.nanoTime());
        memoryProvider.save(greenTeaUserContext, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user prefers green tea", Map.of("source", "test"))));
        SampleMem0OpenJiuwenHandler handler = new SampleMem0OpenJiuwenHandler(
                "openjiuwen-simple-agent",
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai"),
                System.getenv("SAA_SAMPLE_LLM_API_KEY"),
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"),
                envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                Boolean.parseBoolean(envOrDefault("SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY", "false")),
                memoryProvider);

        List<?> agentOutputs = handler.execute(greenTeaUserContext).toList();

        assertThat(agentOutputs).isNotEmpty();
        assertThat(memoryProvider.search(greenTeaUserContext, "green tea", 5))
                .extracting(MemoryProvider.MemoryHit::content)
                .anySatisfy(content -> assertThat(content).containsIgnoringCase("green tea"));
        assertThat(judgeAnswer(agentOutputs)).contains("PASS");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return hasText(value) ? value : fallback;
    }

    private static String judgeAnswer(List<?> agentOutputs) throws Exception {
        String agentAnswer = agentOutputs.toString();
        Map<String, Object> judgeRequest = Map.of(
                "model", envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                "temperature", 0,
                "max_tokens", 16,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a strict test judge. Reply exactly PASS or FAIL."),
                        Map.of("role", "user", "content", """
                                The memory says: the user prefers green tea.
                                The user asked about their preference.
                                Does the answer correctly use the memory and identify green tea as the preference?

                                Answer:
                                %s
                                """.formatted(agentAnswer))));
        HttpRequest judgeHttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(envOrDefault(
                        "SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"))))
                .header("Authorization", "Bearer " + System.getenv("SAA_SAMPLE_LLM_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(judgeRequest)))
                .build();
        HttpResponse<String> judgeResponse = HTTP.send(judgeHttpRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(judgeResponse.statusCode()).isBetween(200, 299);
        JsonNode judgeContent = JSON.readTree(judgeResponse.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content");
        return judgeContent.asText();
    }

    private static String chatCompletionsUrl(String apiBase) {
        String normalized = String.valueOf(apiBase).replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
