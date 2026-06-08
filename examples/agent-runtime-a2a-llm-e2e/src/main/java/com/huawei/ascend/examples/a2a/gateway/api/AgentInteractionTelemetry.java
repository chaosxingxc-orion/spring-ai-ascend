package com.huawei.ascend.examples.a2a.gateway.api;

import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import java.util.List;

public interface AgentInteractionTelemetry {

    AgentInteractionEvent record(AgentInteractionEvent event);

    List<AgentInteractionEvent> query(String tenantId, String correlationId, int limit);

    long count();
}
