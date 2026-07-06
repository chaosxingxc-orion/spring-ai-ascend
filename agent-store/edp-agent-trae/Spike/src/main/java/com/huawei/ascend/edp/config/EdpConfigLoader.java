package com.huawei.ascend.edp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * edp-config.yaml 配置加载器。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>读取 EDPAgent 专有配置文件。</li>
 *     <li>把 snake_case YAML 字段反序列化为 {@link EdpConfig}。</li>
 *     <li>输出关键配置摘要日志，便于穿刺期间确认配置是否生效。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #load(Path)}：从指定路径加载 edp-config.yaml。</li>
 * </ul>
 */
public class EdpConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpConfigLoader.class);

    /**
     * YAML 解析器。EDP 专有配置使用 snake_case 字段映射。
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /**
     * 加载 edp-config.yaml。
     *
     * @param yamlPath 配置文件路径
     * @return 解析后的 EdpConfig；文件不存在或解析失败时返回默认对象
     */
    public static EdpConfig load(Path yamlPath) {
        // 关键判断：配置路径为空或文件不存在时，不阻断启动，返回默认配置。
        if (yamlPath == null || !Files.exists(yamlPath)) {
            LOGGER.info("edp-config.yaml not found at {}, using defaults", yamlPath);
            return new EdpConfig();
        }
        try {
            // 关键跳转：先读取完整 YAML 文本，再反序列化为 EDP 专有配置对象。
            String content = Files.readString(yamlPath);
            EdpConfig config = YAML_MAPPER.readValue(content, EdpConfig.class);
            LOGGER.info("edp-config.yaml loaded from {}, scope={}, todolistSteps={}, limits.tasks={}",
                    yamlPath,
                    config.getScope() != null ? config.getScope().getAllowed() : "null",
                    config.getTodolistSteps() != null ? config.getTodolistSteps().size() : 0,
                    config.getLimits() != null && config.getLimits().getTasks() != null ? config.getLimits().getTasks().size() : "null");
            return config;
        } catch (IOException e) {
            // 解析失败时降级为默认配置，避免 spike 阶段因配置问题导致服务无法启动。
            LOGGER.warn("Failed to load edp-config.yaml from {}: {}", yamlPath, e.getMessage());
            return new EdpConfig();
        }
    }
}
