package com.huawei.ascend.edp.enhancer;

import com.huawei.ascend.edp.channel.ToolDataChannel;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.rail.McpInterruptRail;
import com.huawei.ascend.edp.rail.VersatileInterruptRail;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EdpaAgentEnhancer 工具数据通道注入测试。
 */
class EdpaAgentEnhancerToolDataChannelTest {

    @Test
    void testBuildBusinessRails_SharesToolDataChannel() throws Exception {
        ToolDataChannel channel = new ToolDataChannel();
        List<AgentRail> rails = EdpaAgentEnhancer.buildBusinessRails(new EdpConfig(), null, channel);

        McpInterruptRail mcpRail = rails.stream()
                .filter(McpInterruptRail.class::isInstance)
                .map(McpInterruptRail.class::cast)
                .findFirst()
                .orElseThrow();
        VersatileInterruptRail versatileRail = rails.stream()
                .filter(VersatileInterruptRail.class::isInstance)
                .map(VersatileInterruptRail.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(rails.size() >= 2);
        assertSame(channel, readField(mcpRail, "toolDataChannel"));
        assertSame(channel, readField(versatileRail, "toolDataChannel"));
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
