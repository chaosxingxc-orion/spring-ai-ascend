package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.discovery.AgentCardSummary;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.discovery.RoutingContext;
import com.huawei.ascend.service.spi.discovery.RuntimeRoute;
import java.util.List;
import java.util.Objects;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/**
 * Discovery-metadata mask over an {@link AgentDirectory}. Agent cards served
 * to external callers are rewritten so every endpoint field ({@code url} and
 * each supported interface's url) points at the gateway-fronted route
 * {@code <publicBaseUrl>/v1/agents/<agentId>/a2a} instead of the runtime's own
 * address — serving a runtime card verbatim would leak back-end topology.
 * This is the only permitted rewrite: {@link #listAgents} and
 * {@link #resolveRoute} pass through unchanged because the gateway itself must
 * reach the real runtime endpoints, and A2A payloads are never touched.
 */
public final class MaskedAgentDirectory implements AgentDirectory {

    private final AgentDirectory delegate;
    private final String publicBaseUrl;

    public MaskedAgentDirectory(AgentDirectory delegate, String publicBaseUrl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("publicBaseUrl is required");
        }
        String normalized = publicBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.publicBaseUrl = normalized;
    }

    @Override
    public AgentCard getAgentCard(String agentId, String tenantId) {
        AgentCard card = delegate.getAgentCard(agentId, tenantId);
        String maskedUrl = publicBaseUrl + "/v1/agents/" + agentId.trim() + "/a2a";
        return AgentCard.builder(card)
                .url(maskedUrl)
                .supportedInterfaces(maskInterfaces(card.supportedInterfaces(), maskedUrl))
                .build();
    }

    @Override
    public List<AgentCardSummary> listAgents(String tenantId) {
        return delegate.listAgents(tenantId);
    }

    @Override
    public RuntimeRoute resolveRoute(String agentId, String tenantId, RoutingContext routingContext) {
        return delegate.resolveRoute(agentId, tenantId, routingContext);
    }

    private static List<AgentInterface> maskInterfaces(List<AgentInterface> interfaces, String maskedUrl) {
        if (interfaces == null) {
            return null;
        }
        return interfaces.stream()
                .map(agentInterface -> new AgentInterface(
                        agentInterface.protocolBinding(),
                        maskedUrl,
                        agentInterface.tenant(),
                        agentInterface.protocolVersion()))
                .toList();
    }
}
