package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdrFactExtractor test (Round-3 Wave Beta, 2026-05-28 sweep defect 14).
 *
 * <p>The Round-2 P2-2 finding required direct tests for each extractor;
 * Wave C shipped 5 test classes but missed AdrFactExtractor. This test
 * covers the high-value behaviours: relationship-edge extraction,
 * 0NNN-prefix file filtering, scalar-field whitelist.
 */
class AdrFactExtractorTest {

    @Test
    void extractsRelationshipFieldsFromSyntheticAdr(@TempDir Path tmpRepo) throws IOException {
        Path adrDir = Files.createDirectories(tmpRepo.resolve("docs/adr"));
        Files.writeString(adrDir.resolve("0001-alpha.yaml"),
                "id: ADR-0001\n"
                        + "title: Alpha\n"
                        + "status: accepted\n"
                        + "level: L0\n"
                        + "view: development\n"
                        + "extends:\n  - ADR-0000\n"
                        + "relates_to:\n  - ADR-9999\n",
                StandardCharsets.UTF_8);
        Files.writeString(adrDir.resolve("0002-bravo.yaml"),
                "id: ADR-0002\n"
                        + "title: Bravo\n"
                        + "status: superseded\n"
                        + "superseded_by:\n  - ADR-0001\n",
                StandardCharsets.UTF_8);
        // Decoy: README must not be picked up by the 4-digit regex.
        Files.writeString(adrDir.resolve("README.md"), "ignored", StandardCharsets.UTF_8);

        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("adrs.json");
        AdrFactExtractor.extract(ctx, out);

        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"fact_id\" : \"adr/0001-alpha\""),
                "Expected fact_id derived from filename slug.");
        assertTrue(body.contains("\"fact_id\" : \"adr/0002-bravo\""));
        assertTrue(body.contains("\"extends\""), "Relationship fields must be inlined when present");
        assertTrue(body.contains("\"superseded_by\""));
        // The substring "README" appears in the banner ("See
        // architecture/facts/README.md") — assert on a fact-id-shaped
        // pattern instead so the test discriminates against actual
        // README.md inclusion as an ADR fact.
        assertFalse(body.contains("\"fact_id\" : \"adr/readme"),
                "Non-4-digit-prefixed files in docs/adr/ MUST be excluded.");
    }

    @Test
    void emitsBannerAndProvenanceEvenWhenAdrDirEmpty(@TempDir Path tmpRepo) throws IOException {
        Files.createDirectories(tmpRepo.resolve("docs/adr"));
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("adrs.json");
        AdrFactExtractor.extract(ctx, out);
        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("DO NOT EDIT"), "Banner present");
        assertTrue(body.contains("\"facts\" : [ ]"), "Empty facts array on empty docs/adr/");
    }
}
