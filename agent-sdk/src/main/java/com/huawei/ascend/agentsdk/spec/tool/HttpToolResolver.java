package com.huawei.ascend.agentsdk.spec.tool;

import com.huawei.ascend.agentsdk.support.ValidationException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpToolResolver implements ToolResolver {
    @Override
    public boolean supports(String scheme) {
        return "http".equalsIgnoreCase(scheme);
    }

    @Override
    public ResolvedTool resolve(ToolSpec spec) {
        Map<String, Object> attributes = spec.ref().attributes();
        String url = required(attributes, "url");
        String method = string(attributes.getOrDefault("method", "POST")).toUpperCase();
        HttpExecutionHandle handle = new HttpExecutionHandle(
                URI.create(url),
                method,
                headers(attributes.get("headers")),
                timeout(attributes.get("timeout")));
        return new WrappableTool(descriptor(spec), handle);
    }

    static ToolDescriptor descriptor(ToolSpec spec) {
        if (spec.name() == null || spec.name().isBlank()) {
            throw new ValidationException("Tool name is required for ref scheme: " + spec.ref().scheme());
        }
        if (spec.description() == null || spec.description().isBlank()) {
            throw new ValidationException("Tool description is required: " + spec.name());
        }
        return new ToolDescriptor(spec.name(), spec.description(), spec.inputSchema(), spec.outputSchema());
    }

    static String required(Map<String, Object> attributes, String key) {
        String value = string(attributes.get(key));
        if (value == null || value.isBlank()) {
            throw new ValidationException("Missing required tool ref attribute: " + key);
        }
        return value;
    }

    static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> headers(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return result;
    }

    static Duration timeout(Object value) {
        if (value == null) {
            return Duration.ofSeconds(30);
        }
        if (value instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        return Duration.parse(String.valueOf(value));
    }
}

