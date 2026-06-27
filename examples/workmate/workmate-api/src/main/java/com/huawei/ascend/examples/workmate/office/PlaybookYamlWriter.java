package com.huawei.ascend.examples.workmate.office;

import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class PlaybookYamlWriter {

    private PlaybookYamlWriter() {}

    public static String render(PlaybookDefinition playbook) {
        Map<String, Object> yaml = toMap(playbook);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(yaml);
    }

    public static Map<String, Object> toMap(PlaybookDefinition playbook) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("id", playbook.id());
        yaml.put("title", playbook.title());
        putIfPresent(yaml, "description", playbook.description());
        putIfPresent(yaml, "accent", playbook.accent());
        putIfPresent(yaml, "expertId", playbook.expertId());
        yaml.put("initPrompt", playbook.initPrompt());
        if (playbook.placements() != null && !playbook.placements().isEmpty()) {
            yaml.put("placements", playbook.placements());
        }
        return yaml;
    }

    private static void putIfPresent(Map<String, Object> yaml, String key, String value) {
        if (value != null && !value.isBlank()) {
            yaml.put(key, value);
        }
    }
}
