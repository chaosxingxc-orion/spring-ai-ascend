package com.huawei.ascend.examples.a2a.gateway.api;

import com.huawei.ascend.examples.a2a.gateway.model.AgentCardSummary;
import com.huawei.ascend.examples.a2a.gateway.model.RoutingContext;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRoute;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;

public interface AgentDiscoveryApi {

    AgentCard getAgentCard(String agentId, String tenantId);

    List<AgentCardSummary> listAgents(String tenantId);

    RuntimeRoute resolveRoute(String agentId, String tenantId, RoutingContext routingContext);
}
