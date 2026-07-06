package com.huawei.ascend.edp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统话术配置管理器。
 *
 * 加载 SysScriptsConfig.yaml 并管理 ask_user、取消确认、异常说明等模板。
 */
public class SysScriptsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SysScriptsConfig.class);

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /** 话术模板字典。 */
    private final Map<String, String> templates = new LinkedHashMap<>();

    public SysScriptsConfig() {
        templates.put("thinking", "正在处理您的请求...");
        templates.put("out_of_scope", "当前请求暂不在可处理范围内。");
        templates.put("ask_user_confirm", "请确认是否继续执行该操作。");
    }

    /**
     * 加载话术模板配置。
     *
     * @param configPath 配置文件路径
     */
    public void load(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return;
        }
        Path path = Path.of(configPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            LOGGER.info("SysScriptsConfig not found at {}, skipping", path);
            return;
        }
        try {
            Map<String, Object> parsed = YAML_MAPPER.readValue(Files.readString(path), Map.class);
            flatten("", parsed);
            aliasCommonKeys();
            aliasGovernancePrefixes();
            LOGGER.info("SysScriptsConfig loaded from {}, templates={}", path, templates.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load SysScriptsConfig from {}: {}", path, e.getMessage());
        }
    }

    /**
     * 获取话术模板。
     *
     * @param key 模板 key
     * @return 模板内容，null 表示不存在
     */
    public String getTemplate(String key) {
        return templates.get(key);
    }

    /**
     * 是否存在该话术 key（话术消费面合规判定 / 兜底用）。
     *
     * @param key 模板 key
     * @return true 表示配置内存在该 key
     */
    public boolean has(String key) {
        return templates.containsKey(key);
    }

    /**
     * 取模板，缺失返回默认值（兜底场景用）。
     *
     * @param key 模板 key
     * @param def 缺失时的默认值
     * @return 模板内容或 def
     */
    public String getOrDefault(String key, String def) {
        return templates.getOrDefault(key, def);
    }

    /**
     * 合并场景级话术（场景级覆盖系统级同名 key）。
     *
     * @param skillScripts Skill 话术字典
     */
    public void mergeSkillScripts(Map<String, String> skillScripts) {
        if (skillScripts != null) {
            templates.putAll(skillScripts);
        }
    }

    /**
     * 做安全变量替换。
     *
     * @param template 模板内容
     * @param vars 变量字典
     * @return 替换后的内容
     */
    public String render(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        if (vars != null) {
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    public Map<String, String> getTemplates() {
        return Map.copyOf(templates);
    }

    private void flatten(String prefix, Map<String, Object> values) {
        if (values == null) return;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> child = new LinkedHashMap<>();
                map.forEach((k, v) -> child.put(String.valueOf(k), v));
                flatten(key, child);
            } else if (value instanceof List<?> list) {
                templates.put(key, joinList(list));
            } else if (value != null) {
                templates.put(key, String.valueOf(value));
            }
        }
    }

    private String joinList(List<?> values) {
        return values.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private void aliasCommonKeys() {
        if (templates.containsKey("thinking.default")) {
            templates.put("thinking", templates.get("thinking.default"));
        }
        if (templates.containsKey("ask_user_confirm.default_confirm")) {
            templates.put("ask_user_confirm", templates.get("ask_user_confirm.default_confirm"));
        }
    }

    /**
     * governance/scriptconfig.yaml 嵌套结构适配：剥离 scriptconfig.general_scripts. 前缀，
     * 使消费者能以平铺 key（tool_start / interrupt_start 等）查找话术。
     */
    private void aliasGovernancePrefixes() {
        Map<String, String> aliases = new LinkedHashMap<>();
        // YAML 顶层有 scriptconfig: 键，flatten 产生 scriptconfig.general_scripts.xxx
        String prefix = "scriptconfig.general_scripts.";
        for (Map.Entry<String, String> e : templates.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                String shortKey = e.getKey().substring(prefix.length());
                aliases.put(shortKey, e.getValue());
            }
        }
        templates.putAll(aliases);
    }
}
