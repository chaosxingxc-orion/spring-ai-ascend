package com.huawei.ascend.agentsdk.spec.yaml;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.support.ValidationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class AgentYamlLoader {
    private final AgentYamlEnvironmentResolver environmentResolver;
    private final AgentYamlParser parser;

    public AgentYamlLoader() {
        this(new AgentYamlEnvironmentResolver(), new AgentYamlParser());
    }

    public AgentYamlLoader(AgentYamlEnvironmentResolver environmentResolver, AgentYamlParser parser) {
        this.environmentResolver = environmentResolver;
        this.parser = parser;
    }

    public AgentSpec load(Path yamlPath) {
        Path normalized = yamlPath.toAbsolutePath().normalize();
        try {
            String content = environmentResolver.resolve(java.nio.file.Files.readString(normalized));
            Object parsed = new Yaml().load(content);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new ValidationException("Agent YAML root must be an object: " + normalized);
            }
            return parser.parse(AgentYamlParser.map(parsed), normalized);
        } catch (IOException error) {
            throw new ValidationException("Failed to read agent YAML: " + normalized, error);
        }
    }
}

