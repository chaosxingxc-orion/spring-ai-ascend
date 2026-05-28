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
 * Wave-2 extractor: walks every active module's
 * {@code src/main/resources/application*.yaml} and emits one
 * {@code runtime_config} fact per top-level key.
 *
 * <p>The W2 cut is YAML-source only — Java {@code @ConfigurationProperties}
 * source-AST overlay lands in Wave-4 alongside the
 * {@code CodeSymbolFactExtractor} (so JavaParser is introduced exactly
 * once). The W2 facts already carry actionable signal: posture defaults,
 * profile guards, datasource / clients / model gateway endpoints, etc.
 *
 * <p>Authority: ADR-0154; Rule G-15 sub-clauses .a/.b.
 */
public final class RuntimeConfigFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#RuntimeConfigFactExtractor";

    private RuntimeConfigFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        List<String> modules = List.of(
                "agent-service",
                "agent-bus",
                "agent-execution-engine",
                "agent-middleware",
                "agent-evolve",
                "agent-client",
                "spring-ai-ascend-graphmemory-starter");

        List<Map<String, Object>> facts = new ArrayList<>();
        for (String module : modules) {
            Path resources = ctx.repoRoot().resolve(module).resolve("src/main/resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            List<Path> appYamls = new ArrayList<>();
            try (var stream = Files.list(resources)) {
                stream.forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.startsWith("application") && (name.endsWith(".yaml") || name.endsWith(".yml"))) {
                        appYamls.add(p);
                    }
                });
            }
            appYamls.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path yamlPath : appYamls) {
                String fileName = yamlPath.getFileName().toString();
                String sourcePath = module + "/src/main/resources/" + fileName;
                try (var in = Files.newBufferedReader(yamlPath, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml().load(in);
                    if (!(loaded instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    int idx = 0;
                    List<String> keys = new ArrayList<>();
                    raw.keySet().forEach(k -> keys.add(String.valueOf(k)));
                    keys.sort(Comparator.naturalOrder());
                    for (String key : keys) {
                        Map<String, Object> observed = new LinkedHashMap<>();
                        observed.put("module", module);
                        observed.put("source_file", fileName);
                        observed.put("top_level_key", key);
                        observed.put("value", raw.get(key));
                        facts.add(FactWriter.entry(
                                "runtime-config/" + module + "/" + fileName.replace('.', '-') + "/" + key.replace('.', '-'),
                                "runtime_config",
                                "config",
                                sourcePath,
                                key,
                                EXTRACTOR_ID,
                                ctx.extractorVersion(),
                                ctx.repoCommit(),
                                observed));
                        idx++;
                    }
                    // suppress unused-var lint without changing semantics
                    if (idx < 0) {
                        throw new IllegalStateException("unreachable");
                    }
                }
            }
        }

        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }
}
