package com.huawei.ascend.edp.Redis;

import com.huawei.ascend.edp.config.TodoRedisProperties;
import com.huawei.ascend.edp.todo.RedisTodoStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisTodoStoreHealthTest {

    @Test
    @DisplayName("UC-01: PING + 版本≥6.2 → 健康检查通过返回 true")
    void healthCheck_passesWhenVersionSufficient() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("pong");
        Properties info = new Properties();
        info.setProperty("redis_version", "7.2.0");
        when(conn.info("server")).thenReturn(info);

        TodoRedisProperties props = new TodoRedisProperties();
        RedisTodoStore store = new RedisTodoStore(redis, props);

        assertTrue(store.healthCheck());
        verify(conn).close();
    }

    @Test
    @DisplayName("UC-01: 边界版本 6.2.0 通过")
    void healthCheck_passesAtBoundary6_2() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("pong");
        Properties info = new Properties();
        info.setProperty("redis_version", "6.2.0");
        when(conn.info("server")).thenReturn(info);

        RedisTodoStore store = new RedisTodoStore(redis, new TodoRedisProperties());

        assertTrue(store.healthCheck());
    }

    @Test
    @DisplayName("UC-02 AF-02-B: 版本 5.x → 抛 IllegalStateException")
    void healthCheck_versionTooLowThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("pong");
        Properties info = new Properties();
        info.setProperty("redis_version", "5.0.14");
        when(conn.info("server")).thenReturn(info);

        RedisTodoStore store = new RedisTodoStore(redis, new TodoRedisProperties());

        IllegalStateException ex = assertThrows(IllegalStateException.class, store::healthCheck);
        assertTrue(ex.getMessage().contains("Redis version too low"));
    }

    @Test
    @DisplayName("UC-02: PING 抛异常 → 抛 IllegalStateException")
    void healthCheck_connectionFailureThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenThrow(new RuntimeException("connection refused"));

        RedisTodoStore store = new RedisTodoStore(redis, new TodoRedisProperties());

        IllegalStateException ex = assertThrows(IllegalStateException.class, store::healthCheck);
        assertTrue(ex.getMessage().contains("Redis health check failed"));
    }

    @Test
    @DisplayName("UC-02 AF-02-A: 认证失败（NOAUTH）→ 抛 IllegalStateException")
    void healthCheck_authFailureThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenThrow(new RuntimeException("NOAUTH Authentication required"));

        RedisTodoStore store = new RedisTodoStore(redis, new TodoRedisProperties());

        assertThrows(IllegalStateException.class, store::healthCheck);
    }

    @Test
    @DisplayName("UC-02: 版本信息缺失 → 抛 IllegalStateException")
    void healthCheck_missingVersionThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(conn);
        when(conn.ping()).thenReturn("pong");
        when(conn.info("server")).thenReturn(new Properties());

        RedisTodoStore store = new RedisTodoStore(redis, new TodoRedisProperties());

        assertThrows(IllegalStateException.class, store::healthCheck);
    }
}
