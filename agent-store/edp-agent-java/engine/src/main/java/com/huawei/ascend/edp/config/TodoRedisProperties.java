package com.huawei.ascend.edp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis TodoStore 配置属性。
 *
 * <p>对应配置前缀 {@code edpa.agent.redis}，承载部署模式、连接参数、TodoStore 与 Checkpointer TTL。
 * 设计参考：FEAT_EDPA Redis 存储设计方案 §2.2.1。</p>
 */
@ConfigurationProperties(prefix = "edpa.agent.redis")
public class TodoRedisProperties {

    /** 部署模式：single | sentinel | cluster。 */
    private String mode = "single";

    /** 单机模式主机。 */
    private String host = "localhost";

    /** 单机模式端口。 */
    private int port = 6379;

    /** 认证密码（环境变量 EDPA_REDIS_PASSWORD 注入）。 */
    private String password;

    /** 数据库索引。 */
    private int database = 0;

    /** 连接建立超时（毫秒）。 */
    private int connectTimeoutMs = 5000;

    /** Socket 读写超时（毫秒）。 */
    private int socketTimeoutMs = 10000;

    /** 哨兵模式配置。 */
    private SentinelConfig sentinel = new SentinelConfig();

    /** 集群模式配置。 */
    private ClusterConfig cluster = new ClusterConfig();

    /** TodoStore 配置。 */
    private TodoConfig todo = new TodoConfig();

    /** Checkpointer TTL（分钟），UC-18。 */
    private int checkpointerTtlMinutes = 60;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public SentinelConfig getSentinel() {
        return sentinel;
    }

    public void setSentinel(SentinelConfig sentinel) {
        this.sentinel = sentinel;
    }

    public ClusterConfig getCluster() {
        return cluster;
    }

    public void setCluster(ClusterConfig cluster) {
        this.cluster = cluster;
    }

    public TodoConfig getTodo() {
        return todo;
    }

    public void setTodo(TodoConfig todo) {
        this.todo = todo;
    }

    public int getCheckpointerTtlMinutes() {
        return checkpointerTtlMinutes;
    }

    public void setCheckpointerTtlMinutes(int checkpointerTtlMinutes) {
        this.checkpointerTtlMinutes = checkpointerTtlMinutes;
    }

    /** TodoStore 配置：Key 前缀、TTL、读时续期。 */
    public static class TodoConfig {
        /** Redis Key 前缀，默认 {@code edpa}。 */
        private String keyPrefix = "edpa";

        /** TTL（秒），默认 3600（60min）。 */
        private long ttlSeconds = 3600;

        /** 读时是否续期，默认 true（UC-04）。 */
        private boolean refreshOnRead = true;

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public boolean isRefreshOnRead() {
            return refreshOnRead;
        }

        public void setRefreshOnRead(boolean refreshOnRead) {
            this.refreshOnRead = refreshOnRead;
        }
    }

    /** 哨兵模式配置。 */
    public static class SentinelConfig {
        /** 主节点名（如 mymaster）。 */
        private String master;

        /** 哨兵节点列表（host:port）。 */
        private List<String> nodes = new ArrayList<>();

        /** 哨兵认证密码。 */
        private String password;

        public String getMaster() {
            return master;
        }

        public void setMaster(String master) {
            this.master = master;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** 集群模式配置。 */
    public static class ClusterConfig {
        /** 集群节点列表（host:port）。 */
        private List<String> nodes = new ArrayList<>();

        /** 最大重定向次数。 */
        private int maxRedirects = 3;

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }
    }
}
