package com.huawei.ascend.examples.deepresearch.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MCP tool that GETs a URL and returns the response body as text. The tool
 * caps the response at {@link #MAX_BODY_BYTES} to keep the JSON-RPC payload
 * sized for an LLM context, and surfaces all errors (network failure, non-2xx
 * response, malformed URL) through the MCP {@code isError} channel rather than
 * the JSON-RPC error envelope — that's the contract the agent-runtime adapter
 * maps to a tool-level failure the agent can recover from.
 */
@Component
public class FetchUrlTool {
    private static final Logger LOG = LoggerFactory.getLogger(FetchUrlTool.class);
    private static final int MAX_BODY_BYTES = 16_384;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String name() {
        return "fetch_url";
    }

    public Map<String, Object> describe() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name());
        tool.put("description", "Fetch a URL and return the response body as text (truncated to "
                + MAX_BODY_BYTES + " bytes).");
        tool.put("inputSchema", Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string", "description", "Absolute URL to fetch via HTTP GET.")),
                "required", List.of("url")));
        return tool;
    }

    public Map<String, Object> call(Map<String, Object> arguments) {
        String url = arguments == null ? "" : String.valueOf(arguments.getOrDefault("url", ""));
        if (url.isBlank()) {
            return errorResult("Missing required argument: url");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException error) {
            return errorResult("Malformed url: " + error.getMessage());
        }
        if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            return errorResult("Only http and https schemes are supported");
        }

        long started = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            String body = response.body() == null ? "" : response.body();
            String truncated = body.length() > MAX_BODY_BYTES ? body.substring(0, MAX_BODY_BYTES) : body;
            LOG.info("fetch_url done url={} status={} bytes={} latencyMs={}",
                    url, response.statusCode(), body.length(), elapsedMs);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return errorResult("HTTP " + response.statusCode() + " from " + url);
            }
            return successResult(truncated, response.statusCode(), body.length(), elapsedMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return errorResult("Interrupted while fetching " + url);
        } catch (Exception error) {
            LOG.warn("fetch_url failed url={} errorClass={} message={}",
                    url, error.getClass().getSimpleName(), error.getMessage());
            return errorResult(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static Map<String, Object> successResult(String text, int status, int totalBytes, long latencyMs) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "structuredContent", Map.of(
                        "status", status,
                        "totalBytes", totalBytes,
                        "latencyMs", latencyMs),
                "isError", false);
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", message)),
                "isError", true);
    }
}