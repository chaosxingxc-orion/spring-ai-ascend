package com.huawei.ascend.examples.workmate.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PromptEnhanceService {

    private static final String SYSTEM_PROMPT =
            "你是 WorkMate 任务描述润色助手。将用户的任务描述改写为更清晰、更可执行的表述，保持原意，不要添加用户未提及的需求。只输出润色后的文本，不要解释。";

    private final WorkmateLlmProperties llm;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PromptEnhanceService(WorkmateLlmProperties llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    public String enhance(String text, String expertId) {
        if (!llm.isConfigured()) {
            throw new IllegalStateException(
                    "LLM not configured. Set WORKMATE_LLM_API_KEY (and optional WORKMATE_LLM_API_BASE / WORKMATE_LLM_MODEL).");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", llm.modelName(),
                    "temperature", 0.3,
                    "max_tokens", 1024,
                    "messages", List.of(
                            Map.of("role", "system", "content", buildSystemPrompt(expertId)),
                            Map.of("role", "user", "content", trimmed)));

            String json = objectMapper.writeValueAsString(body);
            String url = normalizeApiBase(llm.apiBase()) + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + llm.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "LLM enhance failed: HTTP " + response.statusCode() + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("LLM enhance returned empty content");
            }
            return content.asText().trim();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LLM enhance failed: " + ex.getMessage(), ex);
        }
    }

    private static String buildSystemPrompt(String expertId) {
        if (expertId == null || expertId.isBlank()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + " 当前用户选择了专家「" + expertId + "」，可在表述中体现该领域语境，但不要虚构能力。";
    }

    private static String normalizeApiBase(String apiBase) {
        String base = apiBase == null ? "" : apiBase.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
