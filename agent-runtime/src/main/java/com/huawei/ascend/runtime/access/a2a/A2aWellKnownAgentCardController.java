package com.huawei.ascend.runtime.access.a2a;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class A2aWellKnownAgentCardController {

    private final AgentCard agentCard;
    private final A2aAccessProperties properties;

    public A2aWellKnownAgentCardController(AgentCard agentCard, A2aAccessProperties properties) {
        this.agentCard = Objects.requireNonNull(agentCard, "agentCard");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @GetMapping("/.well-known/agent-card.json")
    public ResponseEntity<AgentCard> getAgentCard(HttpServletRequest request) {
        return ResponseEntity.ok(resolveUrls(baseUrl(request)));
    }

    private AgentCard resolveUrls(String baseUrl) {
        return AgentCard.builder(agentCard)
                .url(resolveUrl(baseUrl, agentCard.url()))
                .supportedInterfaces(resolveInterfaces(baseUrl))
                .build();
    }

    private List<AgentInterface> resolveInterfaces(String baseUrl) {
        if (agentCard.supportedInterfaces() == null) {
            return null;
        }
        return agentCard.supportedInterfaces().stream()
                .map(agentInterface -> new AgentInterface(
                        agentInterface.protocolBinding(),
                        resolveUrl(baseUrl, agentInterface.url()),
                        agentInterface.tenant(),
                        agentInterface.protocolVersion()))
                .toList();
    }

    private static String resolveUrl(String baseUrl, String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        URI uri = URI.create(url);
        if (uri.isAbsolute()) {
            return url;
        }
        return appendPath(baseUrl, url);
    }

    private static String appendPath(String baseUrl, String path) {
        return stripTrailingSlash(baseUrl) + "/" + stripLeadingSlash(path);
    }

    private static String stripLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String baseUrl(HttpServletRequest request) {
        String configured = properties.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured.strip());
        }
        return requestOrigin(request);
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String requestOrigin(HttpServletRequest request) {
        StringBuilder origin = new StringBuilder(request.getScheme())
                .append("://")
                .append(request.getServerName());
        int port = request.getServerPort();
        if (!isDefaultPort(request.getScheme(), port)) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
