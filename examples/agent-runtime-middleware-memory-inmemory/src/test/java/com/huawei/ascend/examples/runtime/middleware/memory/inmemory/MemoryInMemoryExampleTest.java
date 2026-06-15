package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

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
class MemoryInMemoryExampleTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    void inMemoryMemoryProviderWorksThroughOpenJiuwenHandlerExecution() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "Set SAA_SAMPLE_LLM_API_KEY to run the real LLM example");
        InMemoryMemoryProvider provider = new InMemoryMemoryProvider();
        AgentExecutionContext first = MiddlewareTestFixtures.context("memory-state-a");
        AgentExecutionContext second = MiddlewareTestFixtures.context("memory-state-b");
        provider.save(first, List.of(record("the user prefers green tea")));
        provider.save(second, List.of(record("the user prefers black coffee")));
        SampleMemoryOpenJiuwenHandler handler = new SampleMemoryOpenJiuwenHandler(
                "openjiuwen-simple-agent",
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai"),
                System.getenv("SAA_SAMPLE_LLM_API_KEY"),
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"),
                envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                Boolean.parseBoolean(envOrDefault("SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY", "false")),
                provider);

        List<?> rawResults = handler.execute(first).toList();

        assertThat(rawResults).isNotEmpty();
        assertThat(provider.search(first, "black coffee", 3)).isEmpty();
        assertThat(provider.search(first, "green tea", 3))
                .first()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
        assertThat(provider.records(first))
                .extracting(MemoryProvider.MemoryRecord::content)
                .contains("the user prefers green tea", "green tea");
        assertThat(judgeAnswer(rawResults)).contains("PASS");
    }

    private static MemoryProvider.MemoryRecord record(String content) {
        return new MemoryProvider.MemoryRecord(null, "assistant", content, Map.of("source", "test"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return hasText(value) ? value : fallback;
    }

    private static String judgeAnswer(List<?> rawResults) throws Exception {
        String answer = rawResults.toString();
        Map<String, Object> request = Map.of(
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
                                """.formatted(answer))));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(envOrDefault(
                        "SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"))))
                .header("Authorization", "Bearer " + System.getenv("SAA_SAMPLE_LLM_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        JsonNode content = JSON.readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content");
        return content.asText();
    }

    private static String chatCompletionsUrl(String apiBase) {
        String normalized = String.valueOf(apiBase).replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
