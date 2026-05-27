package com.huawei.ascend.tools.architecture.fragment;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mirror anchor ADRs from docs/adr/&lt;id&gt;-*.yaml to
 * architecture/decisions/&lt;id&gt;.md so Structurizr's `!adrs decisions`
 * directive can import them.
 * <p>
 * Authority: ADR-0150 (Wave 8 docs consolidation). The mirror is byte-identical
 * on re-run (sorted keys + LF line endings). It currently mirrors a fixed
 * anchor set (6 ADRs); a follow-up sub-wave can extend to the full corpus.
 */
public final class AdrMirrorCli {

    private static final List<String> ANCHOR_IDS = List.of(
            "0068", "0119", "0147", "0148", "0149", "0150", "0151"
    );

    private AdrMirrorCli() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = Path.of(argValue(args, "--repo", "."));
        Path adrDir = repoRoot.resolve("docs/adr");
        Path outDir = repoRoot.resolve("architecture/decisions");
        Files.createDirectories(outDir);

        int mirrored = 0;
        for (String id : ANCHOR_IDS) {
            Path source = findYaml(adrDir, id);
            if (source == null) {
                System.err.println("WARN: docs/adr/" + id + "-*.yaml not found");
                continue;
            }
            Path target = outDir.resolve(id + ".md");
            writeMarkdown(source, target, id);
            mirrored++;
        }

        // Structurizr's default decision importer requires every file under !adrs <dir>
        // to match `<number>-<slug>.md`; a README.md would trip a NumberFormatException
        // on "README". Delete any stale README.md left from earlier runs.
        Path staleReadme = outDir.resolve("README.md");
        if (Files.exists(staleReadme)) {
            Files.delete(staleReadme);
        }
        System.out.println("AdrMirrorCli mirrored " + mirrored + " anchor ADR(s) to " + outDir);
    }

    private static Path findYaml(Path adrDir, String id) {
        try (var stream = Files.list(adrDir)) {
            return stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(id + "-") && n.endsWith(".yaml");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeMarkdown(Path source, Path target, String id) throws IOException {
        Map<String, Object> data;
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map<?, ?>)) {
                throw new IOException("ADR yaml is not a map: " + source);
            }
            // Preserve key order via TreeMap-on-write but read from LinkedHashMap-like source.
            data = (Map<String, Object>) loaded;
        }

        String title = String.valueOf(data.getOrDefault("title", id));
        String status = String.valueOf(data.getOrDefault("status", "draft"));
        // Normalise date: SnakeYAML parses bare `2026-05-14` as a java.util.Date; convert to ISO yyyy-MM-dd.
        Object rawDate = data.getOrDefault("date", "");
        String date;
        if (rawDate instanceof java.util.Date d) {
            date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(d);
        } else {
            date = String.valueOf(rawDate);
        }
        String context = String.valueOf(data.getOrDefault("context", ""));
        String decision = String.valueOf(data.getOrDefault("decision", ""));
        String consequences = String.valueOf(data.getOrDefault("consequences", ""));

        try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            w.write("# " + id + ". " + title.trim() + "\n\n");
            w.write("Date: " + date + "\n\n");
            w.write("## Status\n\n");
            w.write(status.trim() + "\n\n");
            w.write("## Context\n\n");
            w.write(context.trim() + "\n\n");
            w.write("## Decision\n\n");
            w.write(decision.trim() + "\n\n");
            w.write("## Consequences\n\n");
            w.write(consequences.trim() + "\n");
        }
        normaliseLineEndings(target);
    }

    private static void normaliseLineEndings(Path p) throws IOException {
        String content = Files.readString(p, StandardCharsets.UTF_8);
        content = content.replace("\r\n", "\n");
        if (!content.endsWith("\n")) {
            content = content + "\n";
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private static String argValue(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }
}
