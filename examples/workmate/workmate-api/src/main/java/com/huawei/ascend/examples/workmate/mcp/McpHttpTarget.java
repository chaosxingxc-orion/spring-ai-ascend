package com.huawei.ascend.examples.workmate.mcp;

import java.net.URI;
import java.net.URISyntaxException;

/** Splits a full MCP URL into HttpClient transport base URI + endpoint path. */
final class McpHttpTarget {

    private final String baseUri;
    private final String endpoint;

    private McpHttpTarget(String baseUri, String endpoint) {
        this.baseUri = baseUri;
        this.endpoint = endpoint;
    }

    String baseUri() {
        return baseUri;
    }

    String endpoint() {
        return endpoint;
    }

    static McpHttpTarget parse(String url, String endpointOverride) {
        URI uri = URI.create(url);
        String base = buildBaseUri(uri);
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            return new McpHttpTarget(base, endpointOverride);
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return new McpHttpTarget(base, "/mcp");
        }
        String endpoint = path;
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            endpoint = endpoint + "?" + uri.getRawQuery();
        }
        return new McpHttpTarget(base, endpoint);
    }

    private static String buildBaseUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid MCP URL: " + uri, ex);
        }
    }
}
