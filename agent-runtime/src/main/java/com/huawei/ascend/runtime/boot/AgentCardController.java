package com.huawei.ascend.runtime.boot;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the agent card independently of the A2A request handler stack.
 * Always available as long as an AgentCard bean exists.
 */
@RestController
public class AgentCardController {

    private final AgentCard agentCard;
    private final RuntimeAccessProperties access;

    public AgentCardController(AgentCard agentCard, RuntimeAccessProperties access) {
        this.agentCard = agentCard;
        this.access = access;
    }

    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard(HttpServletRequest request) {
        return resolveUrls(agentCard, request);
    }

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCardLegacy(HttpServletRequest request) {
        return resolveUrls(agentCard, request);
    }

    private AgentCard resolveUrls(AgentCard card, HttpServletRequest request) {
        final String base = resolveBase(request);
        return AgentCard.builder(card)
                .url(resolveUrl(base, card.url()))
                .provider(card.provider() == null ? null
                        : new AgentProvider(card.provider().organization(),
                                resolveUrl(base, card.provider().url())))
                .supportedInterfaces(card.supportedInterfaces() == null ? List.of() :
                        card.supportedInterfaces().stream()
                                .map(i -> new AgentInterface(
                                        i.protocolBinding(),
                                        resolveUrl(base, i.url()),
                                        i.tenant(), i.protocolVersion()))
                                .toList())
                .build();
    }

    /**
     * A configured {@code agent-runtime.access.a2a.public-base-url} wins (it may carry
     * a path prefix added by a fronting proxy); otherwise the base is derived from the
     * current request. The request-derived path honors {@code X-Forwarded-*} only when
     * the host sets {@code server.forward-headers-strategy=framework} so spring-web's
     * ForwardedHeaderFilter rewrites the request facade.
     */
    private String resolveBase(HttpServletRequest request) {
        String configured = access.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443
                        ? ":" + request.getServerPort() : "");
    }

    private static String resolveUrl(String base, String path) {
        if (path == null || path.isBlank()) {
            return base;
        }
        // A provider-supplied absolute URL is already resolvable — prefixing the
        // local base would yield http://host/http://other-host/a2a.
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
