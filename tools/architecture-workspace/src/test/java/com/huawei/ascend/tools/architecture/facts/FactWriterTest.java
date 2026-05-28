package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FactWriter unit tests (Round-2 Wave C, 2026-05-28 expert-review P2-2).
 *
 * <p>Verifies the LLM-no-author banner appears in the first 5 lines, that
 * the provenance block carries the eight required fields, and that the
 * output uses LF line endings + UTF-8 — these are the byte-identical
 * regeneration invariants Rule G-15.c relies on.
 */
class FactWriterTest {

    @Test
    void writesDoNotEditBannerWithinFirstFiveLines(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("sample.json");
        FactWriter.write(out,
                "tools/architecture-workspace#SampleExtractor",
                "0.0.0-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                List.of(sampleFact()));

        String[] head = Files.readString(out, StandardCharsets.UTF_8).split("\n", 6);
        boolean found = false;
        for (int i = 0; i < Math.min(5, head.length); i++) {
            if (head[i].contains("DO NOT EDIT")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "DO NOT EDIT banner must appear within the first 5 lines (Rule G-15.c).");
    }

    @Test
    void writesProvenanceBlockWithEightRequiredFields(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("sample.json");
        FactWriter.write(out,
                "tools/architecture-workspace#SampleExtractor",
                "0.0.0-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                List.of(sampleFact()));

        String content = Files.readString(out, StandardCharsets.UTF_8);
        for (String required : new String[] {
                "fact_id", "fact_kind", "source_kind", "source_path",
                "extractor", "extractor_version", "repo_commit", "observed_value"
        }) {
            assertTrue(content.contains("\"" + required + "\""),
                    "Provenance field '" + required + "' must appear in the emitted JSON (Rule G-15.b).");
        }
        assertTrue(content.contains("\"_provenance\""), "Document-level _provenance block must be present.");
    }

    @Test
    void writesLfLineEndingsAndUtf8(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("sample.json");
        FactWriter.write(out,
                "tools/architecture-workspace#SampleExtractor",
                "0.0.0-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                List.of(sampleFact()));

        byte[] raw = Files.readAllBytes(out);
        assertFalse(containsCrLf(raw),
                "Generated facts MUST use LF endings for byte-identical regen across Windows + Linux (Rule G-15.c).");
        // last byte should be LF (Jackson + our normalization).
        assertEquals((byte) '\n', raw[raw.length - 1], "File must end with a trailing newline.");
    }

    @Test
    void factEntryHelperOmitsEmptySourceSymbol() {
        Map<String, Object> entry = FactWriter.entry(
                "code-symbol/example",
                "code_symbol",
                "code",
                "agent-service/target/classes/Example.class",
                "",
                "tools/architecture-workspace#SampleExtractor",
                "0.0.0-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                Map.of("k", "v"));
        assertNotNull(entry);
        assertFalse(entry.containsKey("source_symbol"),
                "Empty source_symbol is an optional facet (Round-2 P1-5); omit rather than emit empty string.");
    }

    private static Map<String, Object> sampleFact() {
        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("k", "v");
        return FactWriter.entry(
                "sample/example",
                "code_symbol",
                "code",
                "agent-service/target/classes/Example.class",
                "com.example.Example",
                "tools/architecture-workspace#SampleExtractor",
                "0.0.0-TEST",
                "0123456789abcdef0123456789abcdef01234567",
                obs);
    }

    private static boolean containsCrLf(byte[] data) {
        for (int i = 0; i + 1 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return true;
            }
        }
        return false;
    }
}
