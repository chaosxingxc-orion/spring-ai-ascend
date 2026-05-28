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
 * Wave-2 extractor: emits one {@code adr} fact per
 * {@code docs/adr/&lt;id&gt;-*.yaml} file under the canonical ADR namespace.
 *
 * <p>The observed_value payload includes: id, title, status, date, level,
 * view, authors, plus the relationship lists (extends, relates_to,
 * supersedes, superseded_by, affects_level, affects_view) when present.
 * Free-text rationale fields (context, decision, consequences) are NOT
 * extracted as facts — they remain in the source ADR file as intent
 * (the fact layer carries the structural ADR graph; ADRs themselves
 * remain the canonical rationale layer).
 *
 * <p>Authority: ADR-0154; Rule G-15 sub-clauses .a/.b.
 */
public final class AdrFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#AdrFactExtractor";

    private static final List<String> RELATIONSHIP_FIELDS = List.of(
            "extends", "relates_to", "supersedes", "superseded_by");

    private static final List<String> SCALAR_FIELDS = List.of(
            "id", "title", "status", "date", "level", "view",
            "affects_level", "affects_view");

    private AdrFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        Path adrDir = ctx.repoRoot().resolve("docs/adr");
        List<Path> adrFiles = new ArrayList<>();
        if (Files.isDirectory(adrDir)) {
            try (var stream = Files.list(adrDir)) {
                stream.forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.matches("^[0-9]{4}-.*\\.yaml$")) {
                        adrFiles.add(p);
                    }
                });
            }
        }
        adrFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));

        List<Map<String, Object>> facts = new ArrayList<>();
        for (Path path : adrFiles) {
            String fileName = path.getFileName().toString();
            String sourcePath = "docs/adr/" + fileName;
            try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(in);
                if (!(loaded instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> observed = new LinkedHashMap<>();
                for (String key : SCALAR_FIELDS) {
                    if (raw.containsKey(key)) {
                        observed.put(key, raw.get(key));
                    }
                }
                if (raw.containsKey("authors")) {
                    observed.put("authors", raw.get("authors"));
                }
                for (String key : RELATIONSHIP_FIELDS) {
                    if (raw.containsKey(key)) {
                        observed.put(key, raw.get(key));
                    }
                }
                Object rawId = raw.containsKey("id") ? raw.get("id") : "";
                String id = String.valueOf(rawId);
                String slug = fileName.substring(0, fileName.length() - ".yaml".length()).toLowerCase();
                facts.add(FactWriter.entry(
                        "adr/" + slug,
                        "adr",
                        "adr",
                        sourcePath,
                        id,
                        EXTRACTOR_ID,
                        ctx.extractorVersion(),
                        ctx.repoCommit(),
                        observed));
            }
        }

        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }
}
