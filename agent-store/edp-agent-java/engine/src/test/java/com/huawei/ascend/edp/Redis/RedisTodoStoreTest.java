package com.huawei.ascend.edp.Redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.TodoRedisProperties;
import com.huawei.ascend.edp.todo.RedisTodoStore;

import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisTodoStoreTest {

    private static final String SID = "state:default:edp_agent:conv-test-0001";

    private StringRedisTemplate redis;
    private RedisConnection connection;
    private RedisConnectionFactory connectionFactory;
    private ValueOperations<String, String> valueOps;
    private TodoRedisProperties props;
    private RedisTodoStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        connectionFactory = mock(RedisConnectionFactory.class);
        connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);

        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        props = new TodoRedisProperties();
        props.getTodo().setKeyPrefix("edpa");
        props.getTodo().setTtlSeconds(3600);
        props.getTodo().setRefreshOnRead(true);

        store = new RedisTodoStore(redis, props);
    }

    @Test
    @DisplayName("UC-03: save 写入 Redis + 设置 TTL")
    void save_writesJsonAndTtl() {
        List<TodoItem> todos = sampleTodos();

        store.save(SID, todos);

        verify(valueOps).set(eq("edpa:todo:" + SID), contains("推荐理财"), eq(3600L), eq(TimeUnit.SECONDS));
        verifyNoMoreInteractions(valueOps);
    }

    @Test
    @DisplayName("UC-03: save 空列表不抛异常")
    void save_emptyListNoException() {
        assertDoesNotThrow(() -> store.save(SID, new ArrayList<>()));
        verify(valueOps).set(eq("edpa:todo:" + SID), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-03: save null 列表按空列表处理")
    void save_nullListHandled() {
        assertDoesNotThrow(() -> store.save(SID, null));
        verify(valueOps).set(eq("edpa:todo:" + SID), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-04: load 命中返回 todos + TTL 续期（refresh_on_read=true）")
    void load_hitRefreshesTtl() throws Exception {
        String json = new ObjectMapper().writeValueAsString(sampleTodos());
        when(valueOps.get("edpa:todo:" + SID)).thenReturn(json);
        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(3600L);

        List<TodoItem> result = store.load(SID);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("推荐理财", result.get(0).getContent());
        verify(redis).expire(eq("edpa:todo:" + SID), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-04: refresh_on_read=false 不触发 TTL 续期")
    void load_noRefreshWhenDisabled() throws Exception {
        props.getTodo().setRefreshOnRead(false);
        String json = new ObjectMapper().writeValueAsString(sampleTodos());
        when(valueOps.get("edpa:todo:" + SID)).thenReturn(json);

        List<TodoItem> result = store.load(SID);

        assertEquals(2, result.size());
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("UC-05: load 未命中返回空列表，不触发 EXPIRE")
    void load_missReturnsEmptyAndNoExpire() {
        when(valueOps.get("edpa:todo:" + SID)).thenReturn(null);

        List<TodoItem> result = store.load(SID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("UC-06: 未读场景 load 不调用 → TTL 由 Redis 自然管理")
    void noReadNoTtlRefresh() {
        when(valueOps.get("edpa:todo:" + SID)).thenReturn(null);

        List<TodoItem> result = store.load(SID);

        assertTrue(result.isEmpty());
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("UC-08: Redis Key 使用原始 sessionId（含冒号不转义）")
    void buildKey_usesRawSessionIdWithColons() {
        String sidWithColons = "state:default:edp_agent:conv-1783042165096";

        store.save(sidWithColons, sampleTodos());

        verify(valueOps).set(eq("edpa:todo:state:default:edp_agent:conv-1783042165096"),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-08: exists 使用原始 sessionId")
    void exists_usesRawSessionId() {
        when(redis.hasKey("edpa:todo:" + SID)).thenReturn(true);

        assertTrue(store.exists(SID));

        verify(redis).hasKey("edpa:todo:" + SID);
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("UC-09: exists=false 时降级返回 false")
    void exists_missingReturnsFalse() {
        when(redis.hasKey(anyString())).thenReturn(false);

        assertFalse(store.exists(SID));
    }

    @Test
    @DisplayName("UC-09: exists=null 时降级返回 false")
    void exists_nullReturnsFalse() {
        when(redis.hasKey(anyString())).thenReturn(null);

        assertFalse(store.exists(SID));
    }

    @Test
    @DisplayName("UC-11 AF-11-A: save 中断静默失败，不抛异常")
    void save_failureDegradesSilently() {
        doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> store.save(SID, sampleTodos()));
    }

    @Test
    @DisplayName("UC-11: load 中断返回空列表，不抛异常")
    void load_failureReturnsEmpty() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        List<TodoItem> result = assertDoesNotThrow(() -> store.load(SID));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("UC-11: exists 中断返回 false，不抛异常")
    void exists_failureReturnsFalse() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(store.exists(SID));
    }

    @Test
    @DisplayName("UC-11 AF-11-B: TTL 续期失败仅降级，不抛异常，数据正常返回")
    void load_ttlRefreshFailureDoesNotThrow() throws Exception {
        String json = new ObjectMapper().writeValueAsString(sampleTodos());
        when(valueOps.get("edpa:todo:" + SID)).thenReturn(json);
        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(3600L);
        doThrow(new RuntimeException("expire timeout"))
                .when(redis).expire(anyString(), anyLong(), any(TimeUnit.class));

        List<TodoItem> result = assertDoesNotThrow(() -> store.load(SID));

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("UC-13: 非法 JSON → 删除 Key + 返回空列表，不抛异常")
    void load_corruptJsonDeletesKeyAndReturnsEmpty() {
        when(valueOps.get("edpa:todo:" + SID)).thenReturn("NOT_JSON_{corrupted}");

        List<TodoItem> result = assertDoesNotThrow(() -> store.load(SID));

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redis).delete("edpa:todo:" + SID);
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("UC-15: 含空格 sessionId 正常读写")
    void buildKey_supportsSpaces() {
        String sidWithSpace = "conv test 123";

        store.save(sidWithSpace, sampleTodos());

        verify(valueOps).set(eq("edpa:todo:conv test 123"),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-15: 含中文 sessionId 正常读写（UTF-8）")
    void buildKey_supportsChinese() {
        String sidWithChinese = "conv-理财推荐";

        store.save(sidWithChinese, sampleTodos());

        verify(valueOps).set(eq("edpa:todo:conv-理财推荐"),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-15: 超长 sessionId（300 字符）正常读写")
    void buildKey_supportsExtraLong() {
        String longSid = "a".repeat(300);

        store.save(longSid, sampleTodos());

        verify(valueOps).set(eq("edpa:todo:" + longSid),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-15: 自定义 keyPrefix 生效")
    void buildKey_customPrefix() {
        props.getTodo().setKeyPrefix("myedpa");

        store.save(SID, sampleTodos());

        verify(valueOps).set(eq("myedpa:todo:" + SID),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-15: 集群模式下 Key 不含花括号（hash tag 安全）")
    void buildKey_noBracesForCluster() {
        store.save(SID, sampleTodos());

        verify(valueOps).set(eq("edpa:todo:state:default:edp_agent:conv-test-0001"),
                anyString(), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("UC-04 round-trip: save 写入的 JSON 可被 load 反序列化")
    void saveLoad_roundTrip() throws Exception {
        List<TodoItem> todos = sampleTodos();
        store.save(SID, todos);

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), jsonCaptor.capture(), anyLong(), any(TimeUnit.class));
        String writtenJson = jsonCaptor.getValue();

        when(valueOps.get("edpa:todo:" + SID)).thenReturn(writtenJson);
        props.getTodo().setRefreshOnRead(false);

        List<TodoItem> loaded = store.load(SID);

        assertNotNull(loaded);
        assertEquals(todos.size(), loaded.size());
        assertEquals(todos.get(0).getContent(), loaded.get(0).getContent());
        assertEquals(todos.get(0).getStatus(), loaded.get(0).getStatus());
    }

    @Test
    @DisplayName("delete: 删除 session 的 todo 数据")
    void delete_removesKey() {
        store.delete(SID);

        verify(redis).delete("edpa:todo:" + SID);
    }

    @Test
    @DisplayName("delete: 删除失败静默降级")
    void delete_failureDegradesSilently() {
        doThrow(new RuntimeException("connection refused")).when(redis).delete(anyString());

        assertDoesNotThrow(() -> store.delete(SID));
    }

    private static List<TodoItem> sampleTodos() {
        TodoItem t1 = TodoItem.builder()
                .id("uuid-1")
                .content("推荐理财")
                .activeForm("推荐理财产品")
                .description("推荐理财产品")
                .dependsOn(List.of())
                .build();
        t1.setStatus(TodoStatus.IN_PROGRESS);

        TodoItem t2 = TodoItem.builder()
                .id("uuid-2")
                .content("购买理财")
                .activeForm("购买理财产品")
                .description("购买理财产品")
                .dependsOn(List.of("uuid-1"))
                .build();
        t2.setStatus(TodoStatus.PENDING);

        return new ArrayList<>(List.of(t1, t2));
    }
}
