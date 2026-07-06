package com.huawei.ascend.edp.channel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolDataScope 作用域枚举单元测试。
 *
 * 验证阶段 2 架构包结构生产化中的 channel 包枚举类。
 */
class ToolDataScopeTest {

    @Test
    void testEnumValues() {
        assertEquals(4, ToolDataScope.values().length, "应有 4 个枚举值");
    }

    @Test
    void testEnumNames() {
        assertEquals("SINGLE_CALL", ToolDataScope.SINGLE_CALL.name());
        assertEquals("SINGLE_ROUND", ToolDataScope.SINGLE_ROUND.name());
        assertEquals("TASK", ToolDataScope.TASK.name());
        assertEquals("PERSISTENT", ToolDataScope.PERSISTENT.name());
    }

    @Test
    void testValueOf() {
        assertEquals(ToolDataScope.SINGLE_CALL, ToolDataScope.valueOf("SINGLE_CALL"));
        assertEquals(ToolDataScope.TASK, ToolDataScope.valueOf("TASK"));
    }
}
