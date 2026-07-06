package com.huawei.ascend.edp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * edp-agent.yaml 配置加载器。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>读取 EDPAgent 标准配置文件。</li>
 *     <li>把 YAML 内容反序列化为 {@link EdpAgentConfig}。</li>
 *     <li>在文件缺失或解析失败时返回默认配置，保证 spike 可继续启动。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #load(Path)}：从指定路径加载 edp-agent.yaml。</li>
 * </ul>
 */
public class EdpAgentConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpAgentConfigLoader.class);

    /**
     * YAML 解析器。标准 agent 配置使用 lowerCamelCase 字段映射。
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

    /**
     * 加载 edp-agent.yaml。
     *
     * @param yamlPath 配置文件路径
     * @return 解析后的 EdpAgentConfig；文件不存在或解析失败时返回默认对象
     */
    public static EdpAgentConfig load(Path yamlPath) {
        // 关键判断：配置路径为空或文件不存在时，不阻断启动，返回默认配置。
        if (yamlPath == null || !Files.exists(yamlPath)) {
            LOGGER.info("edp-agent.yaml not found at {}, using defaults", yamlPath);
            return new EdpAgentConfig();
        }
        try {
            // 关键跳转：读取 YAML 文本并反序列化为标准配置对象。
            return YAML_MAPPER.readValue(Files.readString(yamlPath), EdpAgentConfig.class);
        } catch (IOException e) {
            // 解析失败时降级为默认配置，避免 spike 阶段因配置问题导致服务无法启动。
            LOGGER.warn("Failed to load edp-agent.yaml from {}: {}", yamlPath, e.getMessage());
            return new EdpAgentConfig();
        }
    }
}
