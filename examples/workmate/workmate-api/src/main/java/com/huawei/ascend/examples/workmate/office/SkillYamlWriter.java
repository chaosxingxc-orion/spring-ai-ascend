package com.huawei.ascend.examples.workmate.office;

import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Serializes {@link SkillDefinition} to skill.yaml (symmetric with {@link SkillRegistry} parser). */
public final class SkillYamlWriter {

    private SkillYamlWriter() {}

    public static String defaultSkillFile() {
        return "SKILL.md";
    }

    public static String render(SkillDefinition skill, String skillFile) {
        Map<String, Object> yaml = toMap(skill, skillFile);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(yaml);
    }

    public static Map<String, Object> toMap(SkillDefinition skill, String skillFile) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("id", skill.id());
        yaml.put("name", skill.name());
        putIfPresent(yaml, "description", skill.description());
        yaml.put("category", skill.category());
        if (!skill.tags().isEmpty()) {
            yaml.put("tags", skill.tags());
        }
        if (skill.source() != null && !skill.source().isBlank() && !"builtin".equals(skill.source())) {
            yaml.put("source", skill.source());
        }
        if (skill.defaultInstalled()) {
            yaml.put("defaultInstalled", true);
        }
        yaml.put("skillFile", skillFile != null && !skillFile.isBlank() ? skillFile : defaultSkillFile());
        return yaml;
    }

    private static void putIfPresent(Map<String, Object> yaml, String key, String value) {
        if (value != null && !value.isBlank()) {
            yaml.put(key, value);
        }
    }
}
