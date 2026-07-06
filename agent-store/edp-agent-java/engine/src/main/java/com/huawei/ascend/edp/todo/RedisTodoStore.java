package com.huawei.ascend.edp.todo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.TodoRedisProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.openjiuwen.harness.tools.TodoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Todo 持久化存储。
 *
 * <p>设计参考：FEAT_EDPA Redis 存储设计方案 §2.2.3，对应用例 UC-01~UC-26。
 * 替代原文件存储 ({@code TodoTool.load/save})，使用原始 sessionId 作为 Key（不转义）。</p>
 *
 * <p>降级原则（UC-11）：Redis 异常时 {@code load} 返回空列表、{@code save} 静默失败、
 * {@code exists} 返回 false；不回退文件读取，不抛异常终止会话。</p>
 */
public class RedisTodoStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTodoStore.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<TodoItem>> TODO_TYPE = new TypeReference<>() {};

    /** Redis 最低版本要求（UC-01/UC-02 AF-02-B）。 */
    private static final String MIN_REDIS_VERSION = "6.2";

    private final StringRedisTemplate redis;
    private final TodoRedisProperties props;

    public RedisTodoStore(StringRedisTemplate redis, TodoRedisProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * 健康检查（UC-01/UC-02）。
     *
     * <p>初始化阶段调用：PING + INFO server 版本检查。失败抛 {@link IllegalStateException}，
     * Spring 容器启动失败（UC-02）。</p>
     */
    public boolean healthCheck() {
        try {
            RedisConnection conn = redis.getConnectionFactory().getConnection();
            try {
                String pong = conn.ping();
                LOGGER.debug("[EDPA-DIAG] REDIS_PING response={}", pong);
                Properties info = conn.info("server");
                String version = parseRedisVersion(info);
                if (version == null || compareVersion(version, MIN_REDIS_VERSION) < 0) {
                    LOGGER.error("[EDPA-DIAG] REDIS_VERSION_TOO_LOW: {}, require >= {}", version, MIN_REDIS_VERSION);
                    throw new IllegalStateException(
                            "Redis version too low: " + version + ", require >= " + MIN_REDIS_VERSION);
                }
                LOGGER.info("[EDPA-DIAG] REDIS_HEALTH CHECK passed (version={})", version);
                return true;
            } finally {
                conn.close();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[EDPA-DIAG] REDIS_HEALTH CHECK FAILED: {}", e.getMessage());
            throw new IllegalStateException("Redis health check failed", e);
        }
    }

    /**
     * 加载 todos（UC-04 命中 + 续期 / UC-05 未命中 / UC-11 降级 / UC-13 数据损坏）。
     *
     * @param rawSessionId 原始 sessionId（不转义，UC-08/UC-15）
     * @return 永不返回 null；未命中或降级时返回空列表
     */
    public List<TodoItem> load(String rawSessionId) {
        String key = buildKey(rawSessionId);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                LOGGER.debug("[EDPA-DIAG] REDIS_MISS key={} session={} (new or expired)", key, rawSessionId);
                return new ArrayList<>();
            }
            try {
                List<TodoItem> todos = JSON.readValue(json, TODO_TYPE);
                if (LOGGER.isDebugEnabled()) {
                    Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                    LOGGER.debug("[EDPA-DIAG] REDIS_HIT key={} session={} items={} ttl={}s",
                            key, rawSessionId, todos.size(), ttl);
                }
                if (props.getTodo().isRefreshOnRead()) {
                    refreshTtl(key);
                }
                return todos;
            } catch (Exception parseError) {
                // UC-13: 数据损坏 → 删除 Key + 返回空列表
                redis.delete(key);
                LOGGER.error("[EDPA-DIAG] REDIS_CORRUPT key={} session={}", key, rawSessionId);
                return new ArrayList<>();
            }
        } catch (Exception e) {
            // UC-11: 运行期降级，返回空列表
            LOGGER.error("[EDPA-DIAG] REDIS_UNAVAILABLE key={} session={}", key, rawSessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存 todos（UC-03 写入 + TTL / UC-11 AF-11-A 写入中断降级）。
     *
     * @param rawSessionId 原始 sessionId（不转义）
     * @param todos        待持久化的 todo 列表
     */
    public void save(String rawSessionId, List<TodoItem> todos) {
        String key = buildKey(rawSessionId);
        try {
            String json = JSON.writeValueAsString(todos == null ? new ArrayList<>() : todos);
            redis.opsForValue().set(key, json, props.getTodo().getTtlSeconds(), TimeUnit.SECONDS);
            if (LOGGER.isDebugEnabled()) {
                int itemCount = todos == null ? 0 : todos.size();
                String statusSummary = todos == null ? "" : todos.stream()
                        .map(t -> t.getStatus() == null ? "null" : t.getStatus().name())
                        .reduce((a, b) -> a + "," + b).orElse("");
                LOGGER.debug("[EDPA-DIAG] REDIS_SYNC write key={} session={} items={} ttl={}s statuses=[{}]",
                        key, rawSessionId, itemCount, props.getTodo().getTtlSeconds(), statusSummary);
            }
        } catch (Exception e) {
            // UC-11 AF-11-A: 降级继续，不回退文件，本次写入丢失
            LOGGER.error("[EDPA-DIAG] REDIS_WRITE_FAIL key={} session={}", key, rawSessionId, e);
        }
    }

    /**
     * 检查会话是否已有 todo 规划（UC-09）。
     *
     * <p>仅执行 EXISTS 命令，<b>不触发 TTL 续期</b>。</p>
     *
     * @param rawSessionId 原始 sessionId（不转义）
     * @return 存在返回 true；降级或不存在返回 false
     */
    public boolean exists(String rawSessionId) {
        String key = buildKey(rawSessionId);
        try {
            Boolean exists = redis.hasKey(key);
            boolean result = Boolean.TRUE.equals(exists);
            LOGGER.debug("[EDPA-DIAG] REDIS_EXISTS key={} session={} exists={}",
                    key, rawSessionId, result);
            return result;
        } catch (Exception e) {
            LOGGER.error("[EDPA-DIAG] REDIS_UNAVAILABLE key={} session={}", key, rawSessionId, e);
            return false;
        }
    }

    /**
     * 删除 Redis 中该 session 的 Todo 数据。
     *
     * @param rawSessionId 原始 sessionId（不转义）
     */
    public void delete(String rawSessionId) {
        String key = buildKey(rawSessionId);
        try {
            Boolean deleted = redis.delete(key);
            LOGGER.debug("[EDPA-DIAG] REDIS_DELETE key={} session={} deleted={}", key, rawSessionId, deleted);
        } catch (Exception e) {
            LOGGER.error("[EDPA-DIAG] REDIS_DELETE_FAIL key={} session={}", key, rawSessionId, e);
        }
    }

    /**
     * TTL 续期（UC-04 / UC-11 AF-11-B）。
     *
     * <p>{@code refresh_on_read=true} 时由 {@link #load} 调用。续期失败仅 WARN，不抛异常。</p>
     */
    private void refreshTtl(String key) {
        try {
            redis.expire(key, props.getTodo().getTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("[EDPA-DIAG] REDIS_TTL_REFRESH_FAIL key={}", key, e);
        }
    }

    /**
     * 构建 Redis Key（UC-08/UC-15）。
     *
     * <p>格式：{@code {prefix}:todo:{rawSessionId}}，使用原始 sessionId（不转义）。
     * 支持空格、中文、超长 sessionId；集群模式下 Key 不含花括号（hash tag 安全）。</p>
     */
    private String buildKey(String rawSessionId) {
        String prefix = props.getTodo().getKeyPrefix();
        return prefix + ":todo:" + rawSessionId;
    }

    /**
     * 从 Redis INFO server 输出解析版本号。
     */
    private static String parseRedisVersion(Properties info) {
        if (info == null) {
            return null;
        }
        // Spring 的 Properties 视图：key 为 redis_version
        String v = info.getProperty("redis_version");
        if (v != null) {
            return v.trim();
        }
        // 兜底：遍历原始字符串行
        for (String key : info.stringPropertyNames()) {
            if ("redis_version".equalsIgnoreCase(key)) {
                return info.getProperty(key).trim();
            }
        }
        return null;
    }

    /**
     * 语义化版本比较：a 与 b 形如 "7.2.0"。
     *
     * @return 负数表示 a<b，0 表示相等，正数表示 a>b
     */
    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ai = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int bi = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
