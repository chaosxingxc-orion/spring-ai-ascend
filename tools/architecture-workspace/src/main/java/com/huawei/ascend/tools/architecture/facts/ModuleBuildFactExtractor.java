package com.huawei.ascend.tools.architecture.facts;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-2 extractor: emits one {@code build_module} fact per Maven module
 * declared under root {@code pom.xml#&lt;modules&gt;}.
 *
 * <p>The observed_value payload includes: module name, the on-disk
 * {@code <module>/module-metadata.yaml} content normalized
 * (kind / version / semver_compatibility / spi_packages / deployment_plane
 * / allowed_dependencies / forbidden_dependencies / architecture_doc /
 * dfx_doc), plus a {@code module_metadata_path} pointer.
 *
 * <p>Determinism: facts are sorted by module name; map iteration is
 * preserved via {@link LinkedHashMap}; no live timestamps.
 *
 * <p>Authority: ADR-0154 (Fact-Layer Authority); Rule G-15 sub-clauses .a/.b.
 */
public final class ModuleBuildFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#ModuleBuildFactExtractor";

    private ModuleBuildFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        Path repo = ctx.repoRoot();
        List<String> moduleNames = parseRootModules(repo.resolve("pom.xml"));
        moduleNames.sort(Comparator.naturalOrder());

        List<Map<String, Object>> facts = new ArrayList<>();
        for (String module : moduleNames) {
            Path metaPath = repo.resolve(module).resolve("module-metadata.yaml");
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("module", module);
            observed.put("module_metadata_path", module + "/module-metadata.yaml");
            if (Files.isRegularFile(metaPath)) {
                try (var in = Files.newBufferedReader(metaPath, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml().load(in);
                    if (loaded instanceof Map<?, ?> raw) {
                        for (String key : List.of(
                                "kind",
                                "version",
                                "semver_compatibility",
                                "deployment_plane",
                                "spi_packages",
                                "allowed_dependencies",
                                "forbidden_dependencies",
                                "architecture_doc",
                                "dfx_doc")) {
                            if (raw.containsKey(key)) {
                                observed.put(key, raw.get(key));
                            }
                        }
                    }
                }
                observed.put("module_metadata_present", true);
            } else {
                observed.put("module_metadata_present", false);
            }

            facts.add(FactWriter.entry(
                    "build-module/" + module,
                    "build_module",
                    "build",
                    Files.isRegularFile(metaPath) ? module + "/module-metadata.yaml" : "pom.xml",
                    module,
                    EXTRACTOR_ID,
                    ctx.extractorVersion(),
                    ctx.repoCommit(),
                    observed));
        }

        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }

    /**
     * Pull the {@code <modules>/<module>NAME</module></modules>} block from
     * the root pom.xml without dragging in the full Maven model resolver.
     * The Maven reactor only lists modules by literal name; nested elements
     * are not used here, so a tiny line-based parser suffices and keeps
     * the extractor dependency-free beyond snakeyaml.
     */
    private static List<String> parseRootModules(Path pomXml) throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isRegularFile(pomXml)) {
            return names;
        }
        boolean inModules = false;
        for (String raw : Files.readAllLines(pomXml, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.startsWith("<modules>")) {
                inModules = true;
                continue;
            }
            if (line.startsWith("</modules>")) {
                inModules = false;
                continue;
            }
            if (!inModules) {
                continue;
            }
            if (line.startsWith("<!--") || line.isEmpty()) {
                continue;
            }
            if (line.startsWith("<module>") && line.endsWith("</module>")) {
                String inside = line.substring("<module>".length(), line.length() - "</module>".length()).trim();
                if (!inside.isEmpty()) {
                    names.add(inside);
                }
            }
        }
        return names;
    }
}
