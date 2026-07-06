package com.huawei.ascend.edp.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolDataChannel 跨工具数据通道单元测试。
 *
 * 验证阶段 2 架构包结构生产化中的 channel 包核心类。
 * 覆盖：store/get/remove/clear/clearAll 操作，
 * 以及四元组隔离和单字段 dict 设计。
 */
class ToolDataChannelTest {

    private ToolDataChannel channel;
    private ToolDataKey key1;
    private ToolDataKey key2;

    @BeforeEach
    void setUp() {
        channel = new ToolDataChannel();
        key1 = new ToolDataKey("tenant1", "agent1", "ctx1", "task1");
        key2 = new ToolDataKey("tenant2", "agent2", "ctx2", "task2");
    }

    @Test
    void testStoreAndGet() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", "success");
        channel.store(key1, "call_mcp_result", data);
        Map<String, Object> retrieved = channel.get(key1, "call_mcp_result");
        assertNotNull(retrieved, "store 后应能 get");
        assertEquals("success", retrieved.get("result"));
    }

    @Test
    void testGet_NotExist() {
        Map<String, Object> retrieved = channel.get(key1, "nonexistent");
        assertNull(retrieved, "不存在的结果键应返回 null");
    }

    @Test
    void testIsolation_DifferentTenants() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("value", "A");
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("value", "B");

        channel.store(key1, "same_key", data1);
        channel.store(key2, "same_key", data2);

        assertEquals("A", channel.get(key1, "same_key").get("value"), "tenant1 数据应隔离");
        assertEquals("B", channel.get(key2, "same_key").get("value"), "tenant2 数据应隔离");
    }

    @Test
    void testRemove() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", "to_remove");
        channel.store(key1, "temp_result", data);
        assertNotNull(channel.get(key1, "temp_result"));

        channel.remove(key1, "temp_result");
        assertNull(channel.get(key1, "temp_result"), "remove 后应返回 null");
    }

    @Test
    void testRemove_LastFieldCleansKey() {
        // 单字段 dict 设计：只剩一个 resultKey 时，remove 后整个 key 应被清理
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", "only_one");
        channel.store(key1, "only_field", data);
        channel.remove(key1, "only_field");
        // 清理后通过不同 resultKey 查询也应返回 null（scope 已不存在）
        assertNull(channel.get(key1, "any_other"), "最后一个字段删除后，整个 scope 应被清理");
    }

    @Test
    void testRemove_NotExistNoError() {
        // 删除不存在的 resultKey 不应报错
        channel.remove(key1, "nonexistent");
    }

    @Test
    void testClear() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("v", "1");
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("v", "2");
        channel.store(key1, "field1", data1);
        channel.store(key1, "field2", data2);

        channel.clear(key1);
        assertNull(channel.get(key1, "field1"), "clear 后应返回 null");
        assertNull(channel.get(key1, "field2"), "clear 后所有字段应返回 null");
    }

    @Test
    void testClear_OtherKeyUnaffected() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("v", "kept");
        channel.store(key1, "field1", data);
        channel.store(key2, "field2", data);

        channel.clear(key1);
        assertNull(channel.get(key1, "field1"), "key1 应被清理");
        assertNotNull(channel.get(key2, "field2"), "key2 不应受影响");
    }

    @Test
    void testClearAll() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("v", "x");
        channel.store(key1, "f1", data);
        channel.store(key2, "f2", data);

        channel.clearAll();
        assertNull(channel.get(key1, "f1"), "clearAll 后应返回 null");
        assertNull(channel.get(key2, "f2"), "clearAll 后应返回 null");
    }

    @Test
    void testStore_SameKeyOverwrite() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("version", 1);
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("version", 2);

        channel.store(key1, "result", data1);
        channel.store(key1, "result", data2);

        Map<String, Object> retrieved = channel.get(key1, "result");
        assertEquals(2, retrieved.get("version"), "同一 resultKey 应覆盖");
    }

    @Test
    void testStoreAndGetObject_StringValue() {
        channel.store(key1, "mcp_to_versatile_information", "购买第一支理财产品");
        assertEquals("购买第一支理财产品", channel.getObject(key1, "mcp_to_versatile_information"));
        assertNull(channel.get(key1, "mcp_to_versatile_information"), "非 Map 数据通过 get 应返回 null");
    }

    @Test
    void testContainsAndSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", "A");
        channel.store(key1, "fund_recommend_result", data);

        assertTrue(channel.contains(key1, "fund_recommend_result"));
        assertFalse(channel.contains(key1, "Fund_Recommend_Result"), "key 大小写应敏感");
        assertEquals(1, channel.snapshot(key1).size());
        assertTrue(channel.snapshot(key1).containsKey("fund_recommend_result"));
    }

    @Test
    void testStore_BlankKeyIgnored() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", "A");
        channel.store(key1, "", data);
        assertTrue(channel.snapshot(key1).isEmpty(), "空 resultKey 不应写入");
    }
}
