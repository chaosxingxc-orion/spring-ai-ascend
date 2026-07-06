package com.huawei.ascend.edp.config;

import com.huawei.ascend.edp.todo.RedisTodoStore;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.protocol.ProtocolVersion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

/**
 * Redis 连接配置（Lettuce + RESP2 + 连接超时）。
 *
 * <p>设计参考：FEAT_EDPA Redis 存储设计方案 §2.2.2。</p>
 * <ul>
 *   <li>UC-01/UC-02：RESP2 强制（{@code ProtocolVersion.RESP2}）。</li>
 *   <li>UC-07/UC-22~UC-24：single/sentinel/cluster 三模式按 {@code mode} 切换。</li>
 *   <li>UC-16：Lettuce 默认连接池，支持 Checkpointer + TodoStore 共存。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(TodoRedisProperties.class)
public class RedisConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);

    /** 静态持有 RedisTodoStore 实例，供非 Spring 管理的 EdpaAgentEnhancer 取用。 */
    private static volatile RedisTodoStore singletonStore;

    /** 获取已注册的 RedisTodoStore（未启动 Redis 时返回 null，Rail 回落文件路径）。 */
    public static RedisTodoStore getRedisTodoStore() {
        return singletonStore;
    }

    private final TodoRedisProperties props;

    public RedisConfig(TodoRedisProperties props) {
        this.props = props;
    }

    /**
     * 注册 Redis Checkpointer（UC-17）。
     *
     * <p>启动时创建 {@link RedisCheckpointer} 并通过
     * {@link OpenJiuwenCheckpointerConfigurer#setDefault} 注册为全局默认，
     * Core SDK 会话状态将持久化到 Redis（UC-18~UC-21）。</p>
     */
    @PostConstruct
    public void initRedisCheckpointer() {
        String redisUrl = buildRedisUrl(props);
        try {
            Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                    .create(Map.of(
                            "connection", Map.of("url", redisUrl),
                            "ttl", Map.of(
                                    "default_ttl", props.getCheckpointerTtlMinutes(),
                                    "refresh_on_read", true
                            )
                    ));
            OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
            LOGGER.info("[EDPA-DIAG] REDIS_CHECKPOINTER registered (url={}, ttl={}min)",
                    sanitizeUrl(redisUrl), props.getCheckpointerTtlMinutes());
        } catch (Exception e) {
            LOGGER.error("[EDPA-DIAG] REDIS_CHECKPOINTER register failed", e);
            throw new IllegalStateException("Redis Checkpointer registration failed", e);
        }
    }

    private static String buildRedisUrl(TodoRedisProperties props) {
        String auth = (props.getPassword() != null && !props.getPassword().isBlank())
                ? ":" + props.getPassword() + "@" : "";
        return "redis://" + auth + props.getHost() + ":" + props.getPort()
                + "/" + props.getDatabase();
    }

    private static String sanitizeUrl(String url) {
        return url.replaceAll("://[^@]*@", "://***:***@");
    }

    /**
     * 构建 Lettuce 连接工厂：按 {@code mode} 分发 single/sentinel/cluster。
     *
     * <p>RESP2 强制 + socket 超时 + 连接建立超时，保证 UC-01 健康检查可发现版本/认证问题。</p>
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(TodoRedisProperties props) {
        ClientOptions options = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                        .build())
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder()
                        .clientOptions(options)
                        .commandTimeout(Duration.ofMillis(props.getSocketTimeoutMs()));

        org.springframework.data.redis.connection.RedisConfiguration redisConfig;
        String mode = props.getMode() == null ? "single" : props.getMode().toLowerCase();
        switch (mode) {
            case "sentinel" -> redisConfig = buildSentinelConfig(props);
            case "cluster" -> redisConfig = buildClusterConfig(props);
            default -> redisConfig = buildStandaloneConfig(props);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                (org.springframework.data.redis.connection.RedisConfiguration) redisConfig,
                clientBuilder.build());
        factory.afterPropertiesSet();
        LOGGER.info("[EDPA-DIAG] REDIS_CONFIG mode={} host={} port={} db={} resp2=true",
                mode, props.getHost(), props.getPort(), props.getDatabase());
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * 注册 RedisTodoStore Bean（UC-03~UC-11 主路径）。
     *
     * <p>启动时调用 {@link RedisTodoStore#healthCheck()} 做健康检查（UC-01/UC-02），
     * 失败则容器启动失败。</p>
     */
    @Bean
    public RedisTodoStore redisTodoStore(StringRedisTemplate redisTemplate,
                                         TodoRedisProperties props) {
        RedisTodoStore store = new RedisTodoStore(redisTemplate, props);
        store.healthCheck();
        singletonStore = store;
        return store;
    }

    private RedisStandaloneConfiguration buildStandaloneConfig(TodoRedisProperties props) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(
                props.getHost(), props.getPort());
        cfg.setDatabase(props.getDatabase());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            cfg.setPassword(props.getPassword());
        }
        return cfg;
    }

    private RedisSentinelConfiguration buildSentinelConfig(TodoRedisProperties props) {
        RedisSentinelConfiguration cfg = new RedisSentinelConfiguration();
        cfg.master(props.getSentinel().getMaster());
        cfg.setDatabase(props.getDatabase());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            cfg.setPassword(props.getPassword());
        }
        for (String node : props.getSentinel().getNodes()) {
            String[] hp = node.split(":");
            cfg.sentinel(hp[0].trim(), Integer.parseInt(hp[1].trim()));
        }
        return cfg;
    }

    private RedisClusterConfiguration buildClusterConfig(TodoRedisProperties props) {
        List<RedisNode> nodes = new ArrayList<>();
        for (String node : props.getCluster().getNodes()) {
            String[] hp = node.split(":");
            nodes.add(new RedisNode(hp[0].trim(), Integer.parseInt(hp[1].trim())));
        }
        RedisClusterConfiguration cfg = new RedisClusterConfiguration();
        cfg.setClusterNodes(nodes);
        cfg.setMaxRedirects(props.getCluster().getMaxRedirects());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            cfg.setPassword(props.getPassword());
        }
        return cfg;
    }
}
