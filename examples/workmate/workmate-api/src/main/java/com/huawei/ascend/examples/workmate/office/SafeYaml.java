package com.huawei.ascend.examples.workmate.office;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Factory for SnakeYAML loaders used to parse office assets (expert/skill/playbook/welcome/connector
 * YAML, including uploaded ZIP contents).
 *
 * <p>Always uses {@link SafeConstructor} so YAML can only produce plain maps/lists/scalars — never
 * arbitrary Java objects (defends against CVE-2022-1471 style deserialization even if a transitive
 * downgrade slips a vulnerable SnakeYAML in). Alias expansion is bounded to prevent billion-laughs
 * style DoS.</p>
 */
final class SafeYaml {

    private static final int MAX_ALIASES = 100;

    private SafeYaml() {
    }

    static Yaml loader() {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }
}
