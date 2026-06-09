package com.huawei.ascend.agentsdk.adapter;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.openjiuwen.core.singleagent.schema.AgentCard;

public final class OpenJiuwenAgentSpecMapper {
    public AgentCard card(AgentSpec spec) {
        return AgentCard.builder()
                .id(spec.name())
                .name(spec.displayName())
                .description(spec.description())
                .build();
    }
}

