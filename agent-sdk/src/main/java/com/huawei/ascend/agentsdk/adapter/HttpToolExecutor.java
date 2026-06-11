package com.huawei.ascend.agentsdk.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.agentsdk.spec.tool.HttpExecutionHandle;
import com.huawei.ascend.agentsdk.support.ToolExecutionException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Executes an {@code http:} tool against its real endpoint. Bodyless methods
 * (GET/HEAD/DELETE) carry the inputs as query parameters; everything else sends
 * them as a JSON body. JSON responses are decoded so the agent sees structured
 * data; anything else is returned as text.
 */
public final class HttpToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ERROR_BODY_PREVIEW_CHARS = 500;

    private final HttpClient client;

    public HttpToolExecutor() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    HttpToolExecutor(HttpClient client) {
        this.client = client;
    }

    public Object execute(HttpExecutionHandle handle, Map<String, Object> inputs) {
        HttpRequest request = buildRequest(handle, inputs == null ? Map.of() : inputs);
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ToolExecutionException(
                    "HTTP tool request failed: " + handle.method() + " " + handle.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException(
                    "HTTP tool request interrupted: " + handle.method() + " " + handle.url(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new ToolExecutionException("HTTP tool " + handle.method() + " " + handle.url()
                    + " answered status " + response.statusCode() + ": " + preview(response.body()));
        }
        return decode(response);
    }

    private HttpRequest buildRequest(HttpExecutionHandle handle, Map<String, Object> inputs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(handle.timeout());
        boolean hasContentType = false;
        for (Map.Entry<String, String> header : handle.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
            hasContentType |= "content-type".equalsIgnoreCase(header.getKey());
        }
        String method = handle.method();
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            return builder.uri(withQuery(handle.url(), inputs))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
        }
        if (!hasContentType) {
            builder.header("Content-Type", "application/json");
        }
        return builder.uri(handle.url())
                .method(method, HttpRequest.BodyPublishers.ofString(toJson(inputs)))
                .build();
    }

    private static URI withQuery(URI url, Map<String, Object> inputs) {
        if (inputs.isEmpty()) {
            return url;
        }
        StringJoiner query = new StringJoiner("&");
        inputs.forEach((key, value) -> query.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
        String existing = url.getRawQuery();
        String merged = existing == null || existing.isBlank() ? query.toString() : existing + "&" + query;
        return URI.create(url.toString().split("\\?", 2)[0] + "?" + merged);
    }

    private static String toJson(Map<String, Object> inputs) {
        try {
            return MAPPER.writeValueAsString(inputs);
        } catch (JsonProcessingException e) {
            throw new ToolExecutionException("HTTP tool inputs are not JSON-encodable: " + e.getMessage(), e);
        }
    }

    private static Object decode(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.toLowerCase(java.util.Locale.ROOT).contains("json") && !body.isBlank()) {
            try {
                return MAPPER.readValue(body, Object.class);
            } catch (JsonProcessingException e) {
                // Declared JSON but unparseable — hand the agent the raw text rather than failing.
                return body;
            }
        }
        return body;
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= ERROR_BODY_PREVIEW_CHARS ? body : body.substring(0, ERROR_BODY_PREVIEW_CHARS) + "…";
    }
}
