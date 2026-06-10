package com.huawei.ascend.agentsdk.spec.yaml;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.model.ModelSpec;
import com.huawei.ascend.agentsdk.spec.prompt.PromptSpec;
import com.huawei.ascend.agentsdk.spec.skill.SkillSourceLoader;
import com.huawei.ascend.agentsdk.spec.skill.SkillSourceSpec;
import com.huawei.ascend.agentsdk.spec.skill.SkillSpec;
import com.huawei.ascend.agentsdk.spec.tool.ToolRef;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import com.huawei.ascend.agentsdk.support.ValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentYamlParser {

    public AgentSpec parse(Map<String, Object> root, Path yamlPath) {
        Path yamlDir = yamlPath.toAbsolutePath().normalize().getParent();
        String schema = string(root.get("schema"));
        if (!"ascend-agent/v1".equals(schema)) {
            throw new ValidationException("Unsupported agent schema: " + schema);
        }
        String name = requiredString(root, "name");
        String displayName = defaultString(string(root.get("displayName")), name);
        String description = requiredString(root, "description");
        Map<String, Object> framework = requiredMap(map(root), "framework");
        String frameworkType = requiredString(framework, "type");
        String agentType = requiredString(framework, "agent");
        Map<String, Object> options = mapOrEmpty(framework.get("options"));
        ModelSpec modelSpec = model(mapOrEmpty(root.get("model")));
        PromptSpec promptSpec = prompt(mapOrEmpty(root.get("prompt")));
        Path cacheRoot = optionalPath(root.get("cacheRoot"), yamlDir);
        List<SkillSourceSpec> skillSources = skillSources(mapOrEmpty(root.get("skills")), yamlDir);
        List<SkillSpec> skillSpecs = new SkillSourceLoader().load(skillSources);
        List<ToolSpec> toolSpecs = toolSpecs(listOrEmpty(root.get("tools")), yamlDir);
        return new AgentSpec(
                schema,
                name,
                displayName,
                description,
                mapOrEmpty(root.get("metadata")),
                cacheRoot,
                frameworkType,
                agentType,
                options,
                modelSpec,
                promptSpec,
                skillSources,
                skillSpecs,
                toolSpecs);
    }

    private ModelSpec model(Map<String, Object> model) {
        return new ModelSpec(
                defaultString(string(model.get("provider")), "openai-compatible"),
                requiredString(model, "name", "model.name"),
                requiredString(model, "baseUrl", "model.baseUrl"),
                requiredString(model, "apiKey", "model.apiKey"),
                booleanValue(model.get("sslVerify"), true, "model.sslVerify"),
                stringMap(mapOrEmpty(model.get("headers"))));
    }

    private PromptSpec prompt(Map<String, Object> prompt) {
        return new PromptSpec(defaultString(string(prompt.get("system")), ""));
    }

    private List<SkillSourceSpec> skillSources(Map<String, Object> skills, Path yamlDir) {
        List<Object> sources = listOrEmpty(skills.get("sources"));
        List<SkillSourceSpec> result = new ArrayList<>();
        for (Object source : sources) {
            if (source instanceof String path) {
                result.add(new SkillSourceSpec("filesystem", resolvePath(yamlDir, path), false));
            } else {
                Map<String, Object> sourceMap = map(source);
                String type = defaultString(string(sourceMap.get("type")), "filesystem");
                result.add(new SkillSourceSpec(
                        type,
                        resolvePath(yamlDir, requiredString(sourceMap, "path")),
                        booleanValue(sourceMap.get("localCache"), false, "skills.sources[].localCache")));
            }
        }
        return List.copyOf(result);
    }

    private List<ToolSpec> toolSpecs(List<Object> tools, Path yamlDir) {
        List<ToolSpec> result = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Object tool : tools) {
            Map<String, Object> toolMap = map(tool);
            String name = requiredString(toolMap, "name", "tools[].name");
            if (!names.add(name)) {
                // The name becomes the global tool registry key — a duplicate silently shadows.
                throw new ValidationException("Duplicate tool name: " + name);
            }
            Object rawRef = toolMap.get("ref");
            if (rawRef == null) {
                throw new ValidationException("Missing required YAML field: tools[].ref (tool: " + name + ")");
            }
            ToolRef ref = toolRef(rawRef, yamlDir);
            result.add(new ToolSpec(
                    name,
                    requiredString(toolMap, "description", "tools[].description (tool: " + name + ")"),
                    mapOrEmpty(toolMap.get("inputSchema")),
                    mapOrEmpty(toolMap.get("outputSchema")),
                    ref,
                    booleanValue(toolMap.get("localCache"), false, "tools[].localCache")));
        }
        return List.copyOf(result);
    }

    private ToolRef toolRef(Object ref, Path yamlDir) {
        if (ref instanceof String value) {
            int split = value.indexOf(':');
            if (split <= 0) {
                throw new ValidationException("Tool ref must be scheme:value: " + value);
            }
            String scheme = value.substring(0, split);
            String rawValue = value.substring(split + 1);
            return new ToolRef(scheme, shorthandAttributes(scheme, rawValue, value));
        }
        Map<String, Object> refMap = map(ref);
        String scheme = requiredString(refMap, "type");
        Map<String, Object> attributes = new LinkedHashMap<>(refMap);
        attributes.remove("type");
        if ("file".equals(scheme) && attributes.containsKey("path")) {
            attributes.put("path", resolvePath(yamlDir, string(attributes.get("path"))).toString());
        }
        return new ToolRef(scheme, attributes);
    }

    /**
     * The string shorthand must produce the attribute keys the built-in resolvers
     * actually read (class/method, url, server/tool) — anything else parses fine
     * and then fails far away at agent build with an unrelated message.
     */
    private static Map<String, Object> shorthandAttributes(String scheme, String rawValue, String full) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        switch (scheme) {
            case "file" -> {
                int hash = rawValue.indexOf('#');
                if (hash <= 0 || hash == rawValue.length() - 1) {
                    throw new ValidationException(
                            "file: tool ref shorthand must be file:com.example.Class#method, got: " + full);
                }
                attributes.put("class", rawValue.substring(0, hash));
                attributes.put("method", rawValue.substring(hash + 1));
            }
            case "http" -> {
                if (rawValue.isBlank()) {
                    throw new ValidationException("http: tool ref shorthand must be http:<url>, got: " + full);
                }
                attributes.put("url", rawValue);
            }
            case "mcp" -> {
                int slash = rawValue.indexOf('/');
                if (slash <= 0 || slash == rawValue.length() - 1) {
                    throw new ValidationException(
                            "mcp: tool ref shorthand must be mcp:server/tool, got: " + full);
                }
                attributes.put("server", rawValue.substring(0, slash));
                attributes.put("tool", rawValue.substring(slash + 1));
            }
            default -> attributes.put("value", rawValue);
        }
        return attributes;
    }

    private static Path optionalPath(Object value, Path base) {
        String text = string(value);
        return text == null || text.isBlank() ? null : resolvePath(base, text);
    }

    private static Path resolvePath(Path base, String value) {
        Path path = Path.of(value);
        Path resolved = path.isAbsolute() ? path : base.resolve(path);
        Path normalized = resolved.toAbsolutePath().normalize();
        if ((value.endsWith(".yaml") || value.endsWith(".yml")) && !Files.exists(normalized)) {
            throw new ValidationException("Referenced file does not exist: " + normalized);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        throw new ValidationException("Expected YAML object, got: " + value);
    }

    static Map<String, Object> mapOrEmpty(Object value) {
        return value == null ? Map.of() : map(value);
    }

    static List<Object> listOrEmpty(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        throw new ValidationException("Expected YAML list, got: " + value);
    }

    static String requiredString(Map<String, Object> map, String key) {
        return requiredString(map, key, key);
    }

    static String requiredString(Map<String, Object> map, String key, String label) {
        String value = string(map.get(key));
        if (value == null || value.isBlank()) {
            throw new ValidationException("Missing required YAML field: " + label);
        }
        return value;
    }

    static String requiredString(Object root, String key) {
        return requiredString(map(root), key);
    }

    static Map<String, Object> requiredMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            throw new ValidationException("Missing required YAML section: " + key);
        }
        return map(value);
    }

    static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static boolean booleanValue(Object value, boolean fallback, String label) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        // Boolean.parseBoolean would map 'yes'/'enabled'/typos to false silently.
        throw new ValidationException("Field '" + label + "' must be true or false, got: " + value);
    }

    static Map<String, String> stringMap(Map<String, Object> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                result.put(key, String.valueOf(value));
            }
        });
        return Map.copyOf(result);
    }
}

