package com.huawei.ascend.runtime.boot;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
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

    public AgentCardController(AgentCard agentCard) {
        this.agentCard = agentCard;
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
        // Honors X-Forwarded-* when a ForwardedHeaderFilter is registered (it rewrites
        // the request facade); raw scheme/host otherwise.
        final String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443
                        ? ":" + request.getServerPort() : "");
        return AgentCard.builder(card)
                .url(resolveUrl(base, card.url()))
                .supportedInterfaces(card.supportedInterfaces() == null ? List.of() :
                        card.supportedInterfaces().stream()
                                .map(i -> new AgentInterface(
                                        i.protocolBinding(),
                                        resolveUrl(base, i.url()),
                                        i.tenant(), i.protocolVersion()))
                                .toList())
                .build();
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
