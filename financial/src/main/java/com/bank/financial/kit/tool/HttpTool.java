package com.bank.financial.kit.tool;

import com.bank.financial.kit.spec.AgentDefinition.ToolDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a declarative {@link ToolDef} into a runnable agent tool — so a business
 * developer wires a backend call in YAML, never hand-coding a tool class.
 *
 * <p>Conventions (kept deliberately small): {@code {name}} placeholders in the
 * URL are filled from the tool inputs; for GET/HEAD/DELETE the remaining inputs
 * become query params, otherwise a JSON body. JSON responses are returned as a
 * Map, everything else as a String. This is how "the agent reads a real number
 * from a backend" instead of inventing one.
 */
public final class HttpTool {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpTool() {
    }

    public static LocalFunction toLocalFunction(ToolDef def) {
        ToolCard card = ToolCard.builder()
                .id(def.name())
                .name(def.name())
                .description(def.description())
                .inputParams(def.inputParams())
                .build();
        return new LocalFunction(card, (Map<String, Object> inputs) -> call(def, inputs));
    }

    private static Object call(ToolDef def, Map<String, Object> inputs) {
        try {
            Map<String, Object> remaining = new LinkedHashMap<>(inputs == null ? Map.of() : inputs);
            String url = fillPath(def.url(), remaining);
            String method = def.method() == null ? "GET" : def.method().toUpperCase();
            boolean bodyless = method.equals("GET") || method.equals("HEAD") || method.equals("DELETE");

            HttpRequest.Builder req;
            if (bodyless) {
                req = HttpRequest.newBuilder(URI.create(appendQuery(url, remaining)))
                        .method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                String json = MAPPER.writeValueAsString(remaining);
                req = HttpRequest.newBuilder(URI.create(url))
                        .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                if (def.headers() == null || !def.headers().containsKey("Content-Type")) {
                    req.header("Content-Type", "application/json");
                }
            }
            req.timeout(Duration.ofSeconds(30));
            if (def.headers() != null) {
                def.headers().forEach(req::header);
            }

            HttpResponse<String> resp = CLIENT.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return Map.of("error", "backend returned HTTP " + resp.statusCode(),
                        "body", preview(resp.body()));
            }
            return decode(resp);
        } catch (Exception e) {
            // Tools must not throw raw — return a structured error the model can read.
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return Map.of("error", "tool call failed: " + msg);
        }
    }

    private static String fillPath(String url, Map<String, Object> inputs) {
        if (url == null) {
            throw new IllegalStateException("tool url is missing");
        }
        String out = url;
        var it = inputs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            String token = "{" + e.getKey() + "}";
            if (out.contains(token)) {
                out = out.replace(token, enc(String.valueOf(e.getValue())));
                it.remove(); // consumed by the path, don't also send as query/body
            }
        }
        return out;
    }

    private static String appendQuery(String url, Map<String, Object> params) {
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url).append(url.contains("?") ? '&' : '?');
        boolean first = true;
        for (var e : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(enc(e.getKey())).append('=').append(enc(String.valueOf(e.getValue())));
            first = false;
        }
        return sb.toString();
    }

    private static Object decode(HttpResponse<String> resp) throws Exception {
        String ct = resp.headers().firstValue("content-type").orElse("");
        String body = resp.body();
        if (ct.contains("json") && body != null && !body.isBlank()) {
            return MAPPER.readValue(body, Object.class);
        }
        return body;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
