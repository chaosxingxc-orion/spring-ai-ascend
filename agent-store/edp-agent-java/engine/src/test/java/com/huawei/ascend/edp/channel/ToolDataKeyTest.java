package com.huawei.ascend.edp.channel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolDataKey 四元组隔离键单元测试。
 *
 * 验证阶段 2 架构包结构生产化中的 channel 包核心类。
 */
class ToolDataKeyTest {

    @Test
    void testEquals_SameValues() {
        ToolDataKey key1 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        ToolDataKey key2 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        assertEquals(key1, key2, "相同四元组应 equals");
    }

    @Test
    void testEquals_DifferentValues() {
        ToolDataKey key1 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        ToolDataKey key2 = new ToolDataKey("tenant2", "agent1", "ctx1", "task1");
        assertNotEquals(key1, key2, "tenantId 不同应 not equals");
    }

    @Test
    void testHashCode_SameValues() {
        ToolDataKey key1 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        ToolDataKey key2 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        assertEquals(key1.hashCode(), key2.hashCode(), "相同四元组 hashCode 应一致");
    }

    @Test
    void testHashCode_DifferentValues() {
        ToolDataKey key1 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        ToolDataKey key2 = new ToolDataKey("tenant2", "agent1", "ctx1", "task1");
        assertNotEquals(key1.hashCode(), key2.hashCode(), "不同四元组 hashCode 应不同");
    }

    @Test
    void testToString() {
        ToolDataKey key = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        String str = key.toString();
        assertTrue(str.contains("tenant=tenant1"), "toString 应包含 tenantId");
        assertTrue(str.contains("agent=agent1"), "toString 应包含 agentId");
        assertTrue(str.contains("context=ctx1"), "toString 应包含 contextId");
        assertTrue(str.contains("task=task1"), "toString 应包含 taskId");
    }

    @Test
    void testGetters() {
        ToolDataKey key = new ToolDataKey("t", "a", "c", "tk");
        assertEquals("t", key.getTenantId());
        assertEquals("a", key.getAgentId());
        assertEquals("c", key.getContextId());
        assertEquals("tk", key.getTaskId());
    }

    @Test
    void testEquals_Null() {
        ToolDataKey key = new ToolDataKey("t", "a", "c", "tk");
        assertNotEquals(key, null, "与 null 不应 equals");
    }

    @Test
    void testEquals_DifferentType() {
        ToolDataKey key = new ToolDataKey("t", "a", "c", "tk");
        assertNotEquals(key, "string", "与不同类型不应 equals");
    }

    @Test
    void testEquals_Self() {
        ToolDataKey key = new ToolDataKey("t", "a", "c", "tk");
        assertEquals(key, key, "与自身应 equals");
    }
}
