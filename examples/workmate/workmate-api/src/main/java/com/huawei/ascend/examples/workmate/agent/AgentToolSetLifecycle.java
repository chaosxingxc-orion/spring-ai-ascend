package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory.McpToolSet;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory.AskUserQuestionToolSet;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory.MemberSendMessageToolSet;
import com.huawei.ascend.examples.workmate.tools.TeamTopicBusToolFactory;
import com.huawei.ascend.examples.workmate.tools.TeamTopicBusToolFactory.TeamTopicBusToolSet;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory.WorkspaceToolSet;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentToolSetLifecycle {

    private final WorkspaceToolFactory toolFactory;
    private final McpOpenJiuwenToolFactory mcpToolFactory;
    private final AskUserQuestionToolFactory askUserQuestionToolFactory;
    private final TeamTopicBusToolFactory teamTopicBusToolFactory;
    private final MemberSendMessageToolFactory memberSendMessageToolFactory;

    public AgentToolSetLifecycle(
            WorkspaceToolFactory toolFactory,
            McpOpenJiuwenToolFactory mcpToolFactory,
            AskUserQuestionToolFactory askUserQuestionToolFactory,
            TeamTopicBusToolFactory teamTopicBusToolFactory,
            MemberSendMessageToolFactory memberSendMessageToolFactory) {
        this.toolFactory = toolFactory;
        this.mcpToolFactory = mcpToolFactory;
        this.askUserQuestionToolFactory = askUserQuestionToolFactory;
        this.teamTopicBusToolFactory = teamTopicBusToolFactory;
        this.memberSendMessageToolFactory = memberSendMessageToolFactory;
    }

    public record RegisteredToolSets(
            WorkspaceToolSet workspace,
            McpToolSet mcp,
            AskUserQuestionToolSet ask,
            TeamTopicBusToolSet teamBus,
            MemberSendMessageToolSet memberSend) {}

    public RegisteredToolSets readFrom(AgentExecutionContext context) {
        return new RegisteredToolSets(
                readToolSet(context),
                readMcpToolSet(context),
                readAskToolSet(context),
                readTeamBusToolSet(context),
                readMemberSendMessageToolSet(context));
    }

    public void unregisterAll(RegisteredToolSets sets, boolean memberSubRun) {
        if (sets.workspace() != null && !memberSubRun) {
            toolFactory.unregister(sets.workspace());
        }
        if (sets.mcp() != null && !memberSubRun) {
            mcpToolFactory.unregister(sets.mcp());
        }
        if (sets.ask() != null) {
            askUserQuestionToolFactory.unregister(sets.ask());
        }
        if (sets.teamBus() != null) {
            teamTopicBusToolFactory.unregister(sets.teamBus());
        }
        if (sets.memberSend() != null && !memberSubRun) {
            memberSendMessageToolFactory.unregister(sets.memberSend());
        }
    }

    private static WorkspaceToolSet readToolSet(AgentExecutionContext context) {
        Optional<Map<String, Object>> state = context.getAgentState();
        if (state.isEmpty()) {
            return null;
        }
        Object value = state.get().get("workmate.toolSet");
        return value instanceof WorkspaceToolSet toolSet ? toolSet : null;
    }

    private static McpToolSet readMcpToolSet(AgentExecutionContext context) {
        Optional<Map<String, Object>> state = context.getAgentState();
        if (state.isEmpty()) {
            return null;
        }
        Object value = state.get().get("workmate.mcpToolSet");
        return value instanceof McpToolSet toolSet ? toolSet : null;
    }

    private static AskUserQuestionToolSet readAskToolSet(AgentExecutionContext context) {
        Optional<Map<String, Object>> state = context.getAgentState();
        if (state.isEmpty()) {
            return null;
        }
        Object value = state.get().get("workmate.askToolSet");
        return value instanceof AskUserQuestionToolSet toolSet ? toolSet : null;
    }

    private static TeamTopicBusToolSet readTeamBusToolSet(AgentExecutionContext context) {
        Optional<Map<String, Object>> state = context.getAgentState();
        if (state.isEmpty()) {
            return null;
        }
        Object value = state.get().get("workmate.teamBusToolSet");
        return value instanceof TeamTopicBusToolSet toolSet ? toolSet : null;
    }

    private static MemberSendMessageToolSet readMemberSendMessageToolSet(AgentExecutionContext context) {
        Optional<Map<String, Object>> state = context.getAgentState();
        if (state.isEmpty()) {
            return null;
        }
        Object value = state.get().get("workmate.memberSendMessageToolSet");
        return value instanceof MemberSendMessageToolSet toolSet ? toolSet : null;
    }
}
