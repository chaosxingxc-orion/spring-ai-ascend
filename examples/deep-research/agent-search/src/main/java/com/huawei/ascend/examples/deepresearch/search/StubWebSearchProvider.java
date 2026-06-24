package com.huawei.ascend.examples.deepresearch.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Fixture-backed stub. Looks up {@code query} substrings in
 * {@code fixtures/search-results.json} (classpath) and returns the matching
 * canned result list. Used by the {@code stub} Spring profile so root e2e
 * tests run without burning Tavily quota.
 * <p>
 * Fixture format:
 * <pre>{@code
 * {
 *   "routes": [
 *     {"contains": ["pricing", "定价"], "results": [ { url, title, snippet, source_kind, score }, ... ]}
 *   ]
 * }
 * }</pre>
 * Missing-match policy: return an empty result list and log a warning, never
 * fabricate. The root agent treats an empty response as a search miss and may
 * retry with a different query.
 */
public final class StubWebSearchProvider implements WebSearchProvider {

    private static final Logger LOG = Logger.getLogger(StubWebSearchProvider.class.getName());
    private static final String DEFAULT_FIXTURE = "/fixtures/search-results.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode fixture;
    private final String fixturePath;

    public StubWebSearchProvider() {
        this(DEFAULT_FIXTURE);
    }

    public StubWebSearchProvider(String classpathResource) {
        this.fixturePath = classpathResource;
        this.fixture = loadFixture(classpathResource);
    }

    private JsonNode loadFixture(String classpathResource) {
        try (InputStream in = StubWebSearchProvider.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not on classpath: " + classpathResource);
            }
            return mapper.readTree(in);
        } catch (IOException ex) {
            throw new IllegalStateException("fixture parse failed: " + classpathResource, ex);
        }
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        String query = request.query() == null ? "" : request.query().toLowerCase(Locale.ROOT);
        JsonNode routes = fixture.path("routes");
        if (!routes.isArray()) {
            return missingMatch(request);
        }
        Iterator<JsonNode> it = routes.elements();
        while (it.hasNext()) {
            JsonNode route = it.next();
            if (matches(route, query)) {
                return new SearchResponse(parseResults(route.path("results"), request.topK()));
            }
        }
        return missingMatch(request);
    }

    private SearchResponse missingMatch(SearchRequest request) {
        LOG.warning(() -> "stub search-results fixture missed query=\"" + request.query()
                + "\" (path=" + fixturePath + ")");
        return new SearchResponse(List.of());
    }

    private static boolean matches(JsonNode route, String lowerQuery) {
        JsonNode contains = route.path("contains");
        if (!contains.isArray()) {
            return false;
        }
        Iterator<JsonNode> it = contains.elements();
        while (it.hasNext()) {
            String token = it.next().asText("").toLowerCase(Locale.ROOT);
            if (!token.isBlank() && lowerQuery.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private List<Result> parseResults(JsonNode results, int topK) {
        if (!results.isArray()) {
            return List.of();
        }
        List<Result> parsed = new ArrayList<>();
        Iterator<JsonNode> it = results.elements();
        while (it.hasNext()) {
            JsonNode node = it.next();
            String url = node.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            String title = node.path("title").asText("");
            String snippet = node.path("snippet").asText("");
            double score = node.path("score").asDouble(0.0);
            SourceKind kind = parseSourceKind(node.path("source_kind").asText(""));
            parsed.add(new Result(url, title, snippet, kind, score));
            if (parsed.size() >= topK) {
                break;
            }
        }
        return parsed;
    }

    private static SourceKind parseSourceKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return SourceKind.BLOG;
        }
        try {
            return SourceKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SourceKind.BLOG;
        }
    }
}