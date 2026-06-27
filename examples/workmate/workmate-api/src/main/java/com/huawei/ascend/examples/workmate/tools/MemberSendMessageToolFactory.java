package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MemberSendMessageToolFactory {

    public record MemberSendMessageToolSet(Tool tool, String agentTag, String toolId) {}

    public Optional<MemberSendMessageToolSet> register(
            UUID sessionId, String agentTag, String memberId, MemberWorkerPool pool) {
        if (sessionId == null || memberId == null || memberId.isBlank() || pool == null) {
            return Optional.empty();
        }
        String toolId = WorkmateToolIds.sendMessage(sessionId, memberId);
        Tool existing = getRegisteredTool(toolId);
        if (existing != null) {
            return Optional.of(new MemberSendMessageToolSet(existing, agentTag, toolId));
        }
        Tool tool = new MemberSendMessageTool(toolId, memberId, pool);
        Runner.resourceMgr().addTool(tool, agentTag);
        return Optional.of(new MemberSendMessageToolSet(tool, agentTag, toolId));
    }

    public void unregister(MemberSendMessageToolSet toolSet) {
        if (toolSet == null) {
            return;
        }
        safeRemove(toolSet.toolId(), toolSet.agentTag());
    }

    public void unregisterMember(UUID sessionId, String agentTag, String memberId) {
        if (sessionId == null || memberId == null || memberId.isBlank()) {
            return;
        }
        safeRemove(WorkmateToolIds.sendMessage(sessionId, memberId), agentTag);
    }

    private static void safeRemove(String toolId, String agentTag) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentTag, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // first registration
        }
    }

    private static Tool getRegisteredTool(String toolId) {
        Object raw = Runner.resourceMgr().getTool(toolId);
        return raw instanceof Tool tool ? tool : null;
    }
}
