package com.huawei.ascend.examples.workmate.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MemorySummarizer {

    private static final String SYSTEM_PROMPT = """
            你是 WorkMate 长期记忆抽取助手。从对话 transcript 中提取可跨会话复用的「稳定偏好/角色/长期事实」。
            规则：
            - 只输出 0-5 条，每条一行，以 "- " 开头
            - 每条不超过 120 字
            - 禁止输出密钥、token、密码、完整文件内容、长命令输出
            - 不要重复已有记忆里已有的内容
            - 若没有值得记住的新信息，只输出 NONE
            """;

    private final WorkmateLlmProperties llm;
    private final MemoryRedactor redactor;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MemorySummarizer(WorkmateLlmProperties llm, MemoryRedactor redactor, ObjectMapper objectMapper) {
        this.llm = llm;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    public List<String> extract(String transcript, String existingMemory) {
        String sanitized = redactor.sanitizeTranscript(transcript);
        if (sanitized.isBlank()) {
            return List.of();
        }
        if (!llm.isConfigured()) {
            return heuristicExtract(sanitized, existingMemory);
        }
        try {
            String userPrompt = """
                    已有记忆：
                    %s

                    本次对话：
                    %s
                    """.formatted(blankToDash(existingMemory), sanitized);

            Map<String, Object> body = Map.of(
                    "model", llm.modelName(),
                    "temperature", 0.2,
                    "max_tokens", 512,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)));

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
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank() || "NONE".equalsIgnoreCase(content)) {
                return List.of();
            }
            return parseBullets(content);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseBullets(String content) {
        List<String> lines = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            } else if (line.startsWith("* ")) {
                line = line.substring(2).trim();
            }
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return redactor.sanitizeEntries(lines);
    }

    /** Fallback when LLM is unavailable — extract short preference-like user lines. */
    static List<String> heuristicExtract(String sanitizedTranscript, String existingMemory) {
        String existing = existingMemory == null ? "" : existingMemory;
        List<String> out = new ArrayList<>();
        for (String raw : sanitizedTranscript.split("\\R")) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.length() > 120) {
                line = line.substring(0, 117) + "…";
            }
            if (existing.contains(line)) {
                continue;
            }
            if (looksLikeMemory(line)) {
                out.add(line);
            }
        }
        if (out.isEmpty()) {
            return List.of();
        }
        return out.size() > 3 ? out.subList(out.size() - 3, out.size()) : out;
    }

    private static boolean looksLikeMemory(String line) {
        String lower = line.toLowerCase();
        return lower.contains("偏好")
                || lower.contains("prefer")
                || lower.contains("记住")
                || lower.contains("我是")
                || lower.contains("i am ")
                || lower.contains("i'm ")
                || lower.contains("以后")
                || lower.contains("默认")
                || lower.contains("role:")
                || line.contains("角色");
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "（无）" : value.trim();
    }

    private static String normalizeApiBase(String apiBase) {
        String base = apiBase == null ? "" : apiBase.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
