package ascend.springai.service.platform.engine;

import ascend.springai.engine.runtime.EngineRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Post-review fix (plan E / P0-4): the engine-envelope contract YAML is
 * loaded by {@link EngineRegistry#readYaml(String)} via two paths — repo-
 * relative filesystem first, then classpath fallback at
 * {@code /docs/contracts/engine-envelope.v1.yaml}. The repo-relative path
 * does not exist in a packaged-jar deployment outside the working tree, so
 * the classpath fallback MUST resolve. This IT proves the YAML is on the
 * agent-service jar classpath (post-Phase-C; pre-Phase-C was agent-platform per ADR-0078) by booting EngineAutoConfiguration with a
 * non-existent filesystem schema path — boot succeeds iff the classpath
 * fallback finds the resource.
 *
 * <p>Mechanism: {@link EngineRegistry#readYaml(String)} interprets its
 * argument first as a filesystem path. If the file does not exist, it falls
 * back to {@code getClass().getResourceAsStream("/" + yamlPath)}. By passing
 * a property value that does NOT resolve on the filesystem but DOES match
 * a classpath resource path, we exercise the fallback branch in isolation.
 *
 * <p>The {@code <resources>} rule in {@code agent-service/pom.xml} (formerly
 * {@code agent-platform/pom.xml} pre-Phase-C per ADR-0078) copies
 * {@code docs/contracts/engine-envelope.v1.yaml} into
 * {@code target/classes/docs/contracts/} at package time so this IT can
 * find the resource without changing the path semantics.
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E91}.
 *
 * <p>Authority: ADR-0076; CLAUDE.md Rule R-C.a / Rule D-5 (Phase 7 audit-response).
 */
class EngineRegistryClasspathBootIT {

    @Test
    void boot_succeeds_when_schema_resolves_only_via_classpath_fallback() {
        // Use a relative path that does NOT exist on the filesystem in this
        // test's cwd. The exact spelling matches the classpath layout in
        // target/classes after the <resources> copy runs.
        String classpathOnlyPath = "docs/contracts/engine-envelope.v1.yaml";
        // Sanity: when the test runs from the module dir (post-Phase-C: agent-service/; pre-Phase-C: agent-platform/ per ADR-0078) as cwd, this path
        // does NOT exist on disk (the YAML lives at <repo-root>/docs/contracts).
        java.nio.file.Path cwdResolved = java.nio.file.Paths.get("").toAbsolutePath()
                .resolve(classpathOnlyPath);
        assertThat(java.nio.file.Files.exists(cwdResolved))
                .as("test premise: relative path must NOT resolve on filesystem in this cwd")
                .isFalse();

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BootConfig.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "app.engine.envelope-schema-path=" + classpathOnlyPath)
                .run()) {
            EngineRegistry registry = ctx.getBean(EngineRegistry.class);
            assertThat(registry).isNotNull();
            assertThat(registry.registeredEngineTypes())
                    .containsExactlyInAnyOrder("graph", "agent-loop");
        }
    }

    @SpringBootConfiguration
    @Import(EngineAutoConfiguration.class)
    static class BootConfig {
    }
}
