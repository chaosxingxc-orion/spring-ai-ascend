package com.huawei.ascend.a2a.memory.memopt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceStore;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MemOpt-backed {@link ExperienceStore}: persists the A2A cross-run experience
 * layer on the MemOpt engine via its framework-neutral HTTP facade —
 * {@code /v1/memory/save} (record) and {@code /v1/memory/search} (recall) — so
 * experience gains MemOpt's persistent, semantic (GAM) memory.
 *
 * <p>MemOpt is delivered as a <b>closed container image</b> (customers run the
 * image, not the Python source); this kit is the thin HTTP <i>client</i> to it —
 * plain JDK {@link HttpClient} + Jackson, no MemOpt types. It speaks only the
 * versioned {@code /v1} contract, never the engine's native NATS / OpenClaw paths.
 * For a networked image, set {@link Options#authToken()} to send a bearer token
 * and use an {@code https://} base URL for TLS.
 *
 * <p>Memory is a side service: this store <b>fails open</b> by default (a slow/down
 * engine yields empty recall and skipped record, never an exception on the agent's
 * path) and trips a {@link Circuit} after repeated failures. Set
 * {@link Options#failOpen()} false for strict callers.
 *
 * <p>Experience is partitioned by {@code a2a-exp::<tenant>::<signature>} as the
 * MemOpt {@code user_id}, so each (tenant, capability-set + task-type) keeps its
 * own lessons; the signature text is the recall query.
 */
public final class MemOptExperienceStore implements ExperienceStore {

    private static final Logger LOG = LoggerFactory.getLogger("a2amem.memopt");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SAVE_PATH = "/v1/memory/save";
    private static final String SEARCH_PATH = "/v1/memory/search";

    /**
     * Timeout / fail-open / circuit tunables, plus an optional bearer token for
     * reaching a containerised MemOpt across a network boundary ({@code authToken}
     * null/blank ⇒ no {@code Authorization} header, for localhost / network-isolated
     * deployments). For TLS, pass an {@code https://} base URL.
     */
    public record Options(Duration timeout, boolean failOpen, int circuitFailureThreshold, long circuitOpenMs,
                          String authToken) {
        public static Options defaults() {
            return new Options(Duration.ofSeconds(2), true, 5, 30_000L, null);
        }

        /** Back-compat: no bearer token. */
        public Options(Duration timeout, boolean failOpen, int circuitFailureThreshold, long circuitOpenMs) {
            this(timeout, failOpen, circuitFailureThreshold, circuitOpenMs, null);
        }
    }

    private final HttpClient http;
    private final String baseUrl;
    private final Options options;
    private final Circuit circuit;

    public MemOptExperienceStore(String baseUrl) {
        this(baseUrl, Options.defaults());
    }

    public MemOptExperienceStore(String baseUrl, Options options) {
        this.baseUrl = normalize(baseUrl);
        this.options = options == null ? Options.defaults() : options;
        this.http = HttpClient.newBuilder().connectTimeout(this.options.timeout()).build();
        this.circuit = new Circuit(this.options.circuitFailureThreshold(), this.options.circuitOpenMs(),
                System::currentTimeMillis);
    }

    @Override
    public void record(String tenantId, CollaborationSignature signature, List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson == null || lesson.text() == null || lesson.text().isBlank()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kind", "a2a-experience");
            meta.put("signature", signatureKey(signature));
            meta.put("sourceAgentId", lesson.sourceAgentId());
            meta.put("reinforcement", lesson.reinforcement());
            records.add(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "role", "assistant",
                    "content", lesson.text(),
                    "metadata", meta));
        }
        if (records.isEmpty()) {
            return;
        }
        String partition = partition(tenantId, signature);
        Map<String, Object> body = Map.of("user_id", partition, "session_id", partition, "records", records);
        if (circuit.isOpen()) {
            degrade("record", "circuit open", null);
            return;
        }
        try {
            post(SAVE_PATH, body);
            circuit.onSuccess();
        } catch (RuntimeException e) {
            circuit.onFailure();
            degrade("record", e.getMessage(), e);
        }
    }

    @Override
    public List<Lesson> recall(String tenantId, CollaborationSignature signature, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        if (circuit.isOpen()) {
            return degradeList("recall", "circuit open", null);
        }
        String partition = partition(tenantId, signature);
        Map<String, Object> body = Map.of(
                "user_id", partition, "session_id", partition,
                "query", signatureQuery(signature), "top_k", topK,
                "scope", Map.of("tenantId", tenantId, "signature", signatureKey(signature)));
        try {
            Map<String, Object> resp = post(SEARCH_PATH, body);
            circuit.onSuccess();
            return toLessons(resp, topK);
        } catch (RuntimeException e) {
            circuit.onFailure();
            return degradeList("recall", e.getMessage(), e);
        }
    }

    @Override
    public void reinforce(String tenantId, CollaborationSignature signature, String lessonText) {
        if (lessonText == null || lessonText.isBlank()) {
            return;
        }
        // Re-confirm by re-recording: the engine reinforces the recurring lesson.
        record(tenantId, signature, List.of(new Lesson(lessonText, null, System.currentTimeMillis())));
    }

    private List<Lesson> toLessons(Map<String, Object> resp, int topK) {
        Object hits = resp.get("hits");
        if (!(hits instanceof List<?> list)) {
            return List.of();
        }
        List<Lesson> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object content = map.get("content");
            if (content == null || String.valueOf(content).isBlank()) {
                continue;
            }
            String source = null;
            int reinforcement = 0;
            if (map.get("metadata") instanceof Map<?, ?> meta) {
                Object s = meta.get("sourceAgentId");
                source = s == null ? null : String.valueOf(s);
                if (meta.get("reinforcement") instanceof Number n) {
                    reinforcement = n.intValue();
                }
            }
            out.add(new Lesson(String.valueOf(content), source, System.currentTimeMillis(), reinforcement));
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(options.timeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            String token = options.authToken();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token.trim());
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("MemOpt " + path + " status " + resp.statusCode());
            }
            String b = resp.body();
            return b == null || b.isBlank() ? Map.of() : MAPPER.readValue(b, MAP_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("MemOpt request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MemOpt request interrupted", e);
        }
    }

    private void degrade(String op, String reason, RuntimeException error) {
        if (!options.failOpen()) {
            throw error != null ? error : new IllegalStateException("MemOpt " + op + " unavailable: " + reason);
        }
        LOG.debug("memopt experience {} degraded ({}); continuing", op, reason);
    }

    private List<Lesson> degradeList(String op, String reason, RuntimeException error) {
        degrade(op, reason, error);
        return List.of();
    }

    /** Stable per-(tenant, signature) MemOpt partition key. */
    static String partition(String tenantId, CollaborationSignature signature) {
        return "a2a-exp::" + (tenantId == null ? "default" : tenantId) + "::" + signatureKey(signature);
    }

    static String signatureKey(CollaborationSignature signature) {
        TreeSet<String> caps = new TreeSet<>(signature.capabilities());
        return String.join(",", caps) + "|" + signature.taskType();
    }

    private static String signatureQuery(CollaborationSignature signature) {
        return String.join(" ", signature.capabilities()) + " " + signature.taskType();
    }

    private static String normalize(String baseUrl) {
        String v = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8077" : baseUrl.trim();
        return v.replaceAll("/+$", "");
    }
}
