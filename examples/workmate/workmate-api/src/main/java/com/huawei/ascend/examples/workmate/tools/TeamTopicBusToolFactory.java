package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberPublisher;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TeamTopicBusToolFactory {

    public record TeamTopicBusToolSet(Tool tool, String agentTag, String toolId) {}

    public Optional<TeamTopicBusToolSet> register(
            TopicBusMemberPublisher publisher, String agentTag, UUID sessionId) {
        if (publisher == null) {
            return Optional.empty();
        }
        String toolId = WorkmateToolIds.teamBusPublish(sessionId);
        safeRemove(toolId, agentTag);
        Tool tool = new TeamTopicBusPublishTool(publisher, toolId);
        Runner.resourceMgr().addTool(tool, agentTag);
        return Optional.of(new TeamTopicBusToolSet(tool, agentTag, toolId));
    }

    public void unregister(TeamTopicBusToolSet toolSet) {
        if (toolSet == null) {
            return;
        }
        safeRemove(toolSet.toolId(), toolSet.agentTag());
    }

    private static void safeRemove(String toolId, String agentTag) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentTag, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // first registration
        }
    }
}
