package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.MsgRole;
import java.util.List;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class AgentScopeWireMessagesTest {

    @Test
    void preservesSystemAndToolWireRolesThroughMetadata() {
        Message system = AgentScopeWireMessages.message("system", "compliance policy");
        Message tool = AgentScopeWireMessages.message("tool", "lookup result");

        assertThat(system.role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(AgentScopeWireMessages.toMsgRole(system)).isEqualTo(MsgRole.SYSTEM);
        assertThat(AgentScopeWireMessages.toMsgRole(tool)).isEqualTo(MsgRole.TOOL);
        assertThat(AgentScopeWireMessages.text(system)).isEqualTo("compliance policy");
    }

    @Test
    void trimsAndCaseFoldsWireRoles() {
        Message padded = AgentScopeWireMessages.message(" Assistant ", "prior reply");

        assertThat(padded.role()).isEqualTo(Message.Role.ROLE_AGENT);
        assertThat(AgentScopeWireMessages.toMsgRole(padded)).isEqualTo(MsgRole.ASSISTANT);
    }

    @Test
    void defaultsUnknownOrMissingWireRolesToUser() {
        assertThat(AgentScopeWireMessages.message(null, "hi").role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(AgentScopeWireMessages.message("customer", "hi").role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(AgentScopeWireMessages.toMsgRole(AgentScopeWireMessages.message(null, "hi")))
                .isEqualTo(MsgRole.USER);
    }

    @Test
    void fallsBackToA2aRoleWhenWireMetadataAbsent() {
        Message agent = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("pong")))
                .build();

        assertThat(AgentScopeWireMessages.toMsgRole(agent)).isEqualTo(MsgRole.ASSISTANT);
        assertThat(AgentScopeWireMessages.text(agent)).isEqualTo("pong");
    }
}
