package com.huawei.ascend.tools.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts that the hard-coded sets in {@link ProfileValidator} stay in lockstep with
 * the human-readable YAML definitions under {@code architecture/profile/}.
 * <p>
 * Without this guard, profile.yaml + relationship-types.yaml +
 * required-properties.yaml could drift from the Java code that enforces them and the
 * gate would silently miss new tags / relationships / required-property classes.
 */
class ProfileYamlParityTest {

    private static final Path REPO = repoRoot();
    private static final Path PROFILE_DIR = REPO.resolve("architecture/profile");

    @Test
    void profileTagsMatchYaml() throws IOException {
        Map<String, Object> profile = loadYaml(PROFILE_DIR.resolve("profile.yaml"));
        @SuppressWarnings("unchecked")
        List<String> tagsFromYaml = (List<String>) profile.get("tags");
        Set<String> yamlSet = new HashSet<>(tagsFromYaml);
        assertEquals(yamlSet, ProfileValidator.PROFILE_TAGS,
                "profile.yaml#tags must match ProfileValidator.PROFILE_TAGS");
    }

    @Test
    void relationshipTypesMatchYaml() throws IOException {
        Map<String, Object> reltypes = loadYaml(PROFILE_DIR.resolve("relationship-types.yaml"));
        @SuppressWarnings("unchecked")
        Map<String, Object> table = (Map<String, Object>) reltypes.get("relationship_types");
        assertEquals(table.keySet(), ProfileValidator.RELATIONSHIP_TYPES,
                "relationship-types.yaml#relationship_types keys must match ProfileValidator.RELATIONSHIP_TYPES");
    }

    @Test
    void commonAndTagSpecificPropertiesMatchYaml() throws IOException {
        Map<String, Object> props = loadYaml(PROFILE_DIR.resolve("required-properties.yaml"));
        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) props.get("required_properties");
        @SuppressWarnings("unchecked")
        List<String> commonFromYaml = (List<String>) required.get("common");
        assertEquals(commonFromYaml, ProfileValidator.COMMON_PROPERTIES,
                "required-properties.yaml#required_properties.common must match ProfileValidator.COMMON_PROPERTIES");

        @SuppressWarnings("unchecked")
        Map<String, Object> byTagYaml = (Map<String, Object>) required.get("by_tag");
        Map<String, List<String>> byTagJava = ProfileValidator.TAG_SPECIFIC;

        assertEquals(byTagYaml.keySet(), byTagJava.keySet(),
                "required-properties.yaml#required_properties.by_tag keys must match Java TAG_SPECIFIC keys");

        for (var entry : byTagJava.entrySet()) {
            @SuppressWarnings("unchecked")
            List<String> yamlList = (List<String>) byTagYaml.get(entry.getKey());
            assertEquals(yamlList, entry.getValue(),
                    "by_tag." + entry.getKey() + " yaml and Java must match");
        }
    }

    @Test
    void statusConditionalPropertiesMatchYaml() throws IOException {
        Map<String, Object> props = loadYaml(PROFILE_DIR.resolve("required-properties.yaml"));
        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) props.get("required_properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> byTagStatusYaml =
                (Map<String, Object>) required.get("by_tag_status");
        Map<String, Map<String, List<String>>> byTagStatusJava =
                ProfileValidator.TAG_STATUS_SPECIFIC;

        assertEquals(byTagStatusYaml.keySet(), byTagStatusJava.keySet(),
                "required-properties.yaml#required_properties.by_tag_status keys must match Java TAG_STATUS_SPECIFIC keys");

        for (var tagEntry : byTagStatusJava.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMapYaml =
                    (Map<String, Object>) byTagStatusYaml.get(tagEntry.getKey());
            assertEquals(statusMapYaml.keySet(), tagEntry.getValue().keySet(),
                    "by_tag_status." + tagEntry.getKey() + " status keys: yaml and Java must match");
            for (var statusEntry : tagEntry.getValue().entrySet()) {
                @SuppressWarnings("unchecked")
                List<String> yamlList =
                        (List<String>) statusMapYaml.get(statusEntry.getKey());
                assertEquals(yamlList, statusEntry.getValue(),
                        "by_tag_status." + tagEntry.getKey() + "." + statusEntry.getKey()
                                + " yaml and Java must match");
            }
        }
    }

    private static Map<String, Object> loadYaml(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            Object parsed = new Yaml().load(in);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                for (var e : map.entrySet()) {
                    typed.put(String.valueOf(e.getKey()), e.getValue());
                }
                return typed;
            }
            throw new IllegalStateException("yaml at " + path + " is not a map root");
        }
    }

    private static Path repoRoot() {
        // Tests run from tools/architecture-workspace/, so repo root is ../..
        return Path.of(".").toAbsolutePath().resolve("../..").normalize();
    }
}
