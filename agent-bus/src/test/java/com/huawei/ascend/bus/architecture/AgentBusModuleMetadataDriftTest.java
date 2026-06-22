package com.huawei.ascend.bus.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POM / module-metadata drift harness for {@code agent-bus} (Stage 1 follow-up
 * MI-002; coverage gap closed in MI-FU-001; key-presence guard added in MI3-004).
 *
 * <p>{@link AgentBusDependencyBoundaryTest} proves via ArchUnit that production
 * bytecode never <em>reaches into</em> a sibling module. It cannot detect
 * configuration drift. This harness reads <b>both</b> {@code agent-bus/pom.xml}
 * <b>and</b> {@code agent-bus/module-metadata.yaml} and asserts the two agree:
 *
 * <ul>
 *   <li>{@code module-metadata.yaml#allowed_dependencies} matches the Stage 12
 *       real-persistence scope (Spring JDBC + Flyway + Postgres driver; Stage 7-11
 *       kept it {@code []}), and the POM has no {@code com.huawei.ascend:*} sibling
 *       at production scope — the same invariant, asserted from both ends.
 *   <li>{@code module-metadata.yaml#forbidden_dependencies} is a non-empty,
 *       intact set, and each declared forbidden sibling is metadata-driven
 *       absent from the POM production graph — so a newly-added forbidden entry
 *       is covered automatically without editing the test (MI-FU-001).
 *   <li>The {@code allowed_dependencies} and {@code forbidden_dependencies}
 *       keys themselves must be present — a deleted or misspelled key fails the
 *       build rather than being read as an empty list (MI3-004).
 *   <li>{@code archunit-junit5} and {@code spring-boot-starter-test} stay
 *       test-scope; they must never enter the production dependency graph.
 * </ul>
 *
 * <p>Authority: {@code agent-bus/module-metadata.yaml}; CLAUDE.md Rule R-C
 * sub-clause .b (Independent Module Evolution). Assertion ID: HA-005.
 *
 * <p>The YAML is parsed with a zero-dependency line scanner (no SnakeYAML) —
 * {@code agent-bus} purity ({@code allowed_dependencies: []}) forbids any new
 * production dependency, including a YAML library. Only the two stable top-level
 * keys ({@code allowed_dependencies}, {@code forbidden_dependencies}) are read,
 * in both inline {@code [a, b]} and block {@code - a\n- b} forms.
 */
class AgentBusModuleMetadataDriftTest {

    private static final File POM_XML = new File("pom.xml");
    private static final File MODULE_METADATA = new File("module-metadata.yaml");
    private static List<Dependency> dependencies;
    private static MetadataList allowedDependenciesMeta;
    private static MetadataList forbiddenDependenciesMeta;

    @BeforeAll
    static void parseSources() throws Exception {
        assertThat(POM_XML)
                .as("agent-bus/pom.xml must be reachable from the surefire working directory "
                  + "(module basedir)")
                .exists();
        dependencies = parsePom(POM_XML);

        assertThat(MODULE_METADATA)
                .as("agent-bus/module-metadata.yaml must be reachable from the surefire working "
                  + "directory (module basedir) — MI-FU-001 widened this harness to read metadata")
                .exists();
        List<String> metaLines = Files.readAllLines(MODULE_METADATA.toPath());
        allowedDependenciesMeta = parseListBlock(metaLines, "allowed_dependencies");
        forbiddenDependenciesMeta = parseListBlock(metaLines, "forbidden_dependencies");
    }

    @Test
    void pom_declared_at_least_one_dependency() {
        assertThat(dependencies)
                .as("pom.xml parse sanity — at least one <dependency> expected")
                .isNotEmpty();
    }

    @Test
    void no_spring_ai_ascend_sibling_at_production_scope() {
        // allowed_dependencies: [] — agent-bus must never depend on a sibling
        // platform module at compile/runtime scope. test-scope siblings (test
        // fixtures) are the only permitted form. The metadata side of this same
        // invariant is checked in metadata_allowed_dependencies_is_empty.
        List<Dependency> productionScope = dependencies.stream()
                .filter(d -> "com.huawei.ascend".equals(d.groupId()))
                .filter(d -> !isTestScope(d.scope()))
                .toList();
        assertThat(productionScope)
                .as("agent-bus allowed_dependencies is [] — no com.huawei.ascend:* sibling may "
                  + "appear at production (compile/runtime) scope")
                .isEmpty();
    }

    @Test
    void archunit_junit5_remains_test_scope() {
        Dependency archunit = find("com.tngtech.archunit", "archunit-junit5");
        assertThat(archunit).as("archunit-junit5 must be declared").isNotNull();
        assertThat(isTestScope(archunit.scope()))
                .as("archunit-junit5 must remain test-scope (MI-002) — it must never enter the "
                  + "production dependency graph")
                .isTrue();
    }

    @Test
    void spring_boot_starter_test_remains_test_scope() {
        Dependency starter = find("org.springframework.boot", "spring-boot-starter-test");
        assertThat(starter).as("spring-boot-starter-test must be declared").isNotNull();
        assertThat(isTestScope(starter.scope()))
                .as("spring-boot-starter-test must remain test-scope (MI-002)")
                .isTrue();
    }

    // ---- module-metadata.yaml drift (MI-FU-001) ---------------------------

    @Test
    void metadata_allowed_dependencies_match_stage12_persistence_scope() {
        assertThat(allowedDependenciesMeta.present())
                .as("module-metadata.yaml must declare an allowed_dependencies key (MI3-004) — a "
                  + "missing key must fail the build, not be silently read as []. The key itself "
                  + "is the invariant; absence is drift, not an empty list.")
                .isTrue();
        List<String> allowed = allowedDependenciesMeta.values();
        assertThat(allowed)
                .as("module-metadata.yaml allowed_dependencies must list the Stage 12 real-"
                  + "persistence dependencies (decision §4 Stage 12 permit); Stage 7-11 kept this "
                  + "list empty. Spring JDBC + Flyway + Postgres driver are now licensed, confined "
                  + "to the persistence.jdbc package by AgentBusForwardingSpiPurityTest.")
                .contains("org.springframework.boot:spring-boot-starter-jdbc",
                          "org.flywaydb:flyway-core",
                          "org.flywaydb:flyway-database-postgresql",
                          "org.postgresql:postgresql");
        assertThat(allowed)
                .as("allowed_dependencies must never admit a concrete broker / MQ client "
                  + "(decision §6.2 always-forbidden; transport is split out of Stage 12)")
                .noneMatch(d -> d.contains("kafka") || d.contains("rabbitmq")
                              || d.contains("rocketmq") || d.contains("nats"));
    }

    @Test
    void metadata_forbidden_dependencies_are_declared_and_absent_from_production_pom() {
        assertThat(forbiddenDependenciesMeta.present())
                .as("module-metadata.yaml must declare a forbidden_dependencies key (MI3-004) — a "
                  + "missing key must fail the build, not be silently read as [].")
                .isTrue();
        List<String> forbiddenDependencies = forbiddenDependenciesMeta.values();

        // Declares the intact forbidden set — fails if any expected sibling is
        // removed from the metadata.
        assertThat(forbiddenDependencies)
                .as("module-metadata.yaml forbidden_dependencies must be a non-empty, intact set "
                  + "(MI-FU-001)")
                .isNotEmpty()
                .contains("agent-runtime", "agent-core", "agent-middleware",
                        "agent-client", "agent-evolve");

        // Metadata-driven guard: iterate whatever forbidden set the metadata
        // declares, so a newly-added forbidden sibling is covered automatically
        // without editing this test.
        Set<String> productionArtifactIds = new HashSet<>();
        for (Dependency d : dependencies) {
            if ("com.huawei.ascend".equals(d.groupId()) && !isTestScope(d.scope())) {
                productionArtifactIds.add(d.artifactId());
            }
        }
        for (String forbidden : forbiddenDependencies) {
            assertThat(productionArtifactIds)
                    .as("forbidden sibling '%s' from module-metadata.yaml must NOT appear at "
                      + "production scope in pom.xml", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static List<Dependency> parsePom(File pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pom);
        List<Dependency> parsed = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            parsed.add(new Dependency(
                    text(el, "groupId"),
                    text(el, "artifactId"),
                    text(el, "scope")));
        }
        return List.copyOf(parsed);
    }

    private static boolean isTestScope(String scope) {
        return scope != null && scope.trim().equalsIgnoreCase("test");
    }

    private static Dependency find(String groupId, String artifactId) {
        return dependencies.stream()
                .filter(d -> groupId.equals(d.groupId()) && artifactId.equals(d.artifactId()))
                .findFirst()
                .orElse(null);
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }

    /**
     * Zero-dependency YAML list reader. Matches a top-level {@code key:} (no
     * leading indentation) and returns its value wrapped in {@link MetadataList},
     * handling both inline {@code [a, b]} (incl. empty {@code []}) and block
     * {@code - a\n- b} forms. Item-level trailing comments ({@code - agent-runtime # reason})
     * are stripped. A <em>missing</em> key returns {@code present == false} so
     * callers distinguish "key absent" (drift that must fail — MI3-004) from "key
     * present with empty value" (legitimate {@code []}). Only the two stable
     * metadata keys are ever requested, so this is deliberately narrow — not a
     * general YAML parser.
     */
    private static MetadataList parseListBlock(List<String> lines, String key) {
        String keyPrefix = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(keyPrefix)) {
                continue;
            }
            String inline = stripComment(line.substring(keyPrefix.length())).trim();
            if (inline.isEmpty()) {
                return new MetadataList(true, collectBlockItems(lines, i + 1));
            }
            if ("[]".equals(inline)) {
                return new MetadataList(true, List.of());
            }
            return new MetadataList(true, parseInlineList(inline));
        }
        return new MetadataList(false, List.of()); // key absent — callers must fail on this
    }

    private static List<String> collectBlockItems(List<String> lines, int fromIndex) {
        List<String> block = new ArrayList<>();
        for (int j = fromIndex; j < lines.size(); j++) {
            String next = lines.get(j);
            if (next.trim().isEmpty()) {
                continue;
            }
            if (!(next.startsWith(" ") || next.startsWith("\t"))) {
                return List.copyOf(block); // de-indented → end of block
            }
            String item = stripComment(next.trim()).trim();
            if (item.startsWith("- ")) {
                block.add(item.substring(2).trim());
            }
        }
        return List.copyOf(block);
    }

    private static List<String> parseInlineList(String inline) {
        String inner = inline.replaceAll("^\\[", "").replaceAll("\\]$", "");
        List<String> items = new ArrayList<>();
        for (String part : inner.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static String stripComment(String s) {
        int hash = s.indexOf('#');
        return hash >= 0 ? s.substring(0, hash) : s;
    }

    /**
     * Result of parsing one metadata list key. {@code present} distinguishes a
     * declared key (with any value, including {@code []}) from a key that is
     * absent from {@code module-metadata.yaml}. The Stage 3 review (MI3-004)
     * requires that a missing {@code allowed_dependencies} or
     * {@code forbidden_dependencies} key fails the build rather than reading as
     * an empty list, so both fields are asserted.
     */
    private record MetadataList(boolean present, List<String> values) {}

    private record Dependency(String groupId, String artifactId, String scope) {}
}
