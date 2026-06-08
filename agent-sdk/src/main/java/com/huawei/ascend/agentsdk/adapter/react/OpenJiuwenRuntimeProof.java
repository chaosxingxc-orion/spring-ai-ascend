package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenJiuwenRuntimeProof {
    private final AgentSpec spec;
    private final List<Tool> tools;

    public OpenJiuwenRuntimeProof(AgentSpec spec, List<Tool> tools) {
        this.spec = spec;
        this.tools = List.copyOf(tools);
    }

    public Map<String, Object> run(Object input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "answer");
        result.put("output", output(input));
        result.put("tools", invokeTools(input));
        result.put("skills", readSkills());
        return result;
    }

    private String output(Object input) {
        return "sdk-proof agent=" + spec.name() + " tools=" + tools.stream()
                .map(tool -> tool.getCard().getName())
                .toList()
                + " skills=" + spec.skillSpecs().stream().map(skill -> skill.name()).toList()
                + " input=" + input;
    }

    private List<Object> invokeTools(Object input) {
        Map<String, Object> toolInput = new LinkedHashMap<>();
        toolInput.put("input", input);
        List<Object> results = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getCard().getName());
            try {
                item.put("result", tool.invoke(toolInput, Map.of("skip_inputs_validate", true)));
            } catch (Exception error) {
                item.put("error", error.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    private List<Map<String, Object>> readSkills() {
        return spec.skillSpecs().stream().map(skill -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill.name());
            try {
                item.put("path", skill.path().toString());
                item.put("content", Files.readString(skill.skillFile()));
            } catch (Exception error) {
                item.put("error", error.getMessage());
            }
            return item;
        }).toList();
    }
}

