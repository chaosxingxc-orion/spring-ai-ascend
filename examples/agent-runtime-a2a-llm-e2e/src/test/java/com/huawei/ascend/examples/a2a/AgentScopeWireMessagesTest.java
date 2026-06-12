package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeMessage;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

class AgentScopeWireMessagesTest {

    @Test
    void preservesSystemAndToolWireRolesThroughMetadata() {
        RuntimeMessage system = AgentScopeWireMessages.message("system", "compliance policy");
        RuntimeMessage tool = AgentScopeWireMessages.message("tool", "lookup result");

        assertThat(system.role()).isEqualTo(RuntimeMessage.Role.USER);
        assertThat(AgentScopeWireMessages.toMsgRole(system)).isEqualTo(MsgRole.SYSTEM);
        assertThat(AgentScopeWireMessages.toMsgRole(tool)).isEqualTo(MsgRole.TOOL);
        assertThat(system.text()).isEqualTo("compliance policy");
    }

    @Test
    void trimsAndCaseFoldsWireRoles() {
        RuntimeMessage padded = AgentScopeWireMessages.message(" Assistant ", "prior reply");

        assertThat(padded.role()).isEqualTo(RuntimeMessage.Role.AGENT);
        assertThat(AgentScopeWireMessages.toMsgRole(padded)).isEqualTo(MsgRole.ASSISTANT);
    }

    @Test
    void defaultsUnknownOrMissingWireRolesToUser() {
        assertThat(AgentScopeWireMessages.message(null, "hi").role()).isEqualTo(RuntimeMessage.Role.USER);
        assertThat(AgentScopeWireMessages.message("customer", "hi").role()).isEqualTo(RuntimeMessage.Role.USER);
        assertThat(AgentScopeWireMessages.toMsgRole(AgentScopeWireMessages.message(null, "hi")))
                .isEqualTo(MsgRole.USER);
    }

    @Test
    void fallsBackToRuntimeRoleWhenWireMetadataAbsent() {
        RuntimeMessage agent = RuntimeMessage.agent("pong");

        assertThat(AgentScopeWireMessages.toMsgRole(agent)).isEqualTo(MsgRole.ASSISTANT);
        assertThat(agent.text()).isEqualTo("pong");
    }
}
