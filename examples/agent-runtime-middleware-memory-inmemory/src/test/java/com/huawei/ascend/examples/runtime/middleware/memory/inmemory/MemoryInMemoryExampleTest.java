package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
        AgentExecutionContext greenTeaUserContext = MiddlewareTestFixtures.context("memory-state-a");
        AgentExecutionContext coffeeUserContext = MiddlewareTestFixtures.context("memory-state-b");
        provider.save(greenTeaUserContext, List.of(record("the user prefers green tea")));
        provider.save(coffeeUserContext, List.of(record("the user prefers black coffee")));
        SampleMemoryOpenJiuwenHandler handler = new SampleMemoryOpenJiuwenHandler(
                "openjiuwen-simple-agent",
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai"),
                System.getenv("SAA_SAMPLE_LLM_API_KEY"),
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"),
                envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                Boolean.parseBoolean(envOrDefault("SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY", "false")),
                provider);

        List<?> agentOutputs = handler.execute(greenTeaUserContext).toList();

        assertThat(agentOutputs).isNotEmpty();
        assertThat(provider.search(greenTeaUserContext, "black coffee", 3)).isEmpty();
        assertThat(provider.search(greenTeaUserContext, "green tea", 3))
                .first()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
        assertThat(provider.records(greenTeaUserContext))
                .extracting(MemoryProvider.MemoryRecord::content)
                .contains("the user prefers green tea", "What drink does the user prefer?");
        String agentAnswer = renderAgentAnswer(agentOutputs);
        assertThat(agentAnswer).containsIgnoringCase("green tea");
        assertThat(judgeAnswer(agentAnswer)).contains("PASS");
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

    private static String renderAgentAnswer(List<?> agentOutputs) {
        return agentOutputs.stream()
                .map(MemoryInMemoryExampleTest::renderOutput)
                .filter(MemoryInMemoryExampleTest::hasText)
                .collect(Collectors.joining("\n"));
    }

    private static String renderOutput(Object output) {
        if (output == null) {
            return "";
        }
        if (output instanceof CharSequence text) {
            return text.toString();
        }
        for (String methodName : List.of("content", "getContent", "text", "getText", "output", "getOutput")) {
            String value = invokeTextAccessor(output, methodName);
            if (hasText(value)) {
                return value;
            }
        }
        for (String fieldName : List.of("payload", "content", "text", "output")) {
            String value = readTextField(output, fieldName);
            if (hasText(value)) {
                return value;
            }
        }
        return String.valueOf(output);
    }

    private static String invokeTextAccessor(Object output, String methodName) {
        try {
            Method method = output.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return "";
            }
            Object value = method.invoke(output);
            return value instanceof CharSequence text ? text.toString() : String.valueOf(value);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return "";
        }
    }

    private static String readTextField(Object output, String fieldName) {
        try {
            Field field = output.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(output);
            return value instanceof CharSequence text ? text.toString() : String.valueOf(value);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return "";
        }
    }

    private static String judgeAnswer(String agentAnswer) throws Exception {
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
