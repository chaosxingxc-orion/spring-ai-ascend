package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Determinism test (Round-2 Wave C, 2026-05-28 P2-2): running the same
 * extractor twice over the same source tree produces byte-identical
 * output. The byte-identical-on-regen contract is the foundation of
 * Rule G-15.c; this test enforces it at the Maven layer so regressions
 * are caught before the gate runs.
 */
class DeterminismTest {

    @Test
    void adrFactExtractorIsByteIdenticalAcrossTwoRuns(@TempDir Path tmpRepo) throws IOException {
        // Two synthetic ADR YAMLs are enough to exercise the sorted-iteration
        // contract (snakeyaml + LinkedHashMap preserve insertion order;
        // ordering across runs must be the same).
        Path adrDir = Files.createDirectories(tmpRepo.resolve("docs/adr"));
        Files.writeString(adrDir.resolve("0001-alpha.yaml"),
                "id: ADR-0001\ntitle: Alpha\nstatus: accepted\nlevel: L0\nview: development\n",
                StandardCharsets.UTF_8);
        Files.writeString(adrDir.resolve("0002-bravo.yaml"),
                "id: ADR-0002\ntitle: Bravo\nstatus: accepted\nlevel: L0\nview: development\nrelates_to:\n  - ADR-0001\n",
                StandardCharsets.UTF_8);

        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path firstOut = tmpRepo.resolve("first/adrs.json");
        Path secondOut = tmpRepo.resolve("second/adrs.json");
        Files.createDirectories(firstOut.getParent());
        Files.createDirectories(secondOut.getParent());

        AdrFactExtractor.extract(ctx, firstOut);
        AdrFactExtractor.extract(ctx, secondOut);

        assertArrayEquals(
                Files.readAllBytes(firstOut),
                Files.readAllBytes(secondOut),
                "Same source tree + same workspace HEAD must yield byte-identical fact files (Rule G-15.c).");
    }
}
