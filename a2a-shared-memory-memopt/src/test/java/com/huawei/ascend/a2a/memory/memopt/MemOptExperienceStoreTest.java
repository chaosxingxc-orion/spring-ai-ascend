package com.huawei.ascend.a2a.memory.memopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the MemOpt-backed ExperienceStore against a stub of MemOpt's HTTP facade
 * (offline, no real engine): the record→/v1/memory/save and recall→/v1/memory/search
 * mapping, lesson parsing, fail-open, and stable partitioning.
 */
class MemOptExperienceStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CollaborationSignature SIG =
            new CollaborationSignature(Set.of("risk", "loan"), "wealth-advice");

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastSaveBody = new AtomicReference<>();
    private final AtomicReference<String> lastSearchBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String searchResponse = "{\"hits\":[]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/memory/save", ex -> respond(ex, lastSaveBody, "{\"accepted\":1,\"status\":\"accepted\"}"));
        server.createContext("/v1/memory/search", ex -> respond(ex, lastSearchBody, searchResponse));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(HttpExchange ex, AtomicReference<String> capture, String body) throws IOException {
        lastAuthHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
        capture.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordPostsToSaveWithPartitionAndLessons() throws Exception {
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl);
        store.record("bank", SIG, List.of(new Lesson("pull credit before pricing", "loan-agent", 1L)));

        Map<String, Object> body = MAPPER.readValue(lastSaveBody.get(), Map.class);
        assertEquals(MemOptExperienceStore.partition("bank", SIG), body.get("user_id"), "partitioned by tenant+signature");
        List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
        assertEquals(1, records.size());
        assertEquals("pull credit before pricing", records.get(0).get("content"));
    }

    @Test
    void recallParsesSearchHitsIntoLessons() {
        searchResponse = "{\"hits\":[{\"content\":\"loan first\",\"metadata\":{\"sourceAgentId\":\"loan-agent\",\"reinforcement\":3}}]}";
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl);
        List<Lesson> hits = store.recall("bank", SIG, 5);
        assertEquals(1, hits.size());
        assertEquals("loan first", hits.get(0).text());
        assertEquals("loan-agent", hits.get(0).sourceAgentId());
        assertEquals(3, hits.get(0).reinforcement());
    }

    @Test
    void failsOpenWhenEngineErrors() {
        status = 500;
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl); // failOpen default
        assertTrue(store.recall("bank", SIG, 5).isEmpty(), "fail-open: empty recall, no throw");
        store.record("bank", SIG, List.of(new Lesson("x", "a", 1L))); // must not throw
    }

    @Test
    void strictModeSurfacesErrors() {
        status = 500;
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl,
                new MemOptExperienceStore.Options(java.time.Duration.ofSeconds(2), false, 5, 30_000L));
        assertThrows(RuntimeException.class, () -> store.recall("bank", SIG, 5));
    }

    @Test
    void sendsBearerTokenWhenConfigured() {
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl,
                new MemOptExperienceStore.Options(java.time.Duration.ofSeconds(2), true, 5, 30_000L, "s3cret"));
        store.record("bank", SIG, List.of(new Lesson("x", "a", 1L)));
        assertEquals("Bearer s3cret", lastAuthHeader.get(), "containerised MemOpt: bearer token forwarded");
    }

    @Test
    void omitsAuthHeaderByDefault() {
        MemOptExperienceStore store = new MemOptExperienceStore(baseUrl); // no token
        store.record("bank", SIG, List.of(new Lesson("x", "a", 1L)));
        assertNull(lastAuthHeader.get(), "no token configured => no Authorization header");
    }

    @Test
    void partitionAndSignatureKeyAreStableAndSorted() {
        // capability set order must not change the key
        CollaborationSignature a = new CollaborationSignature(Set.of("risk", "loan"), "wealth-advice");
        CollaborationSignature b = new CollaborationSignature(Set.of("loan", "risk"), "wealth-advice");
        assertEquals(MemOptExperienceStore.signatureKey(a), MemOptExperienceStore.signatureKey(b));
        assertEquals("loan,risk|wealth-advice", MemOptExperienceStore.signatureKey(a));
        assertFalse(MemOptExperienceStore.partition("bank", a).isBlank());
    }
}
