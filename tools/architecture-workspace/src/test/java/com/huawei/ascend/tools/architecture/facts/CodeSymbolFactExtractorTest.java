package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeSymbolFactExtractor + TestInventoryFactExtractor tests (Round-2 Wave
 * C, 2026-05-28 P2-2 + P2-3).
 *
 * <p>Pinned behaviour: extractors fail closed when an active module lacks
 * compiled classes (default), unless {@code --allow-missing-classes} is
 * passed (fixture / bootstrap mode). The pre-Round-2 silent-skip is gone.
 */
class CodeSymbolFactExtractorTest {

    @Test
    void failsClosedOnMissingTargetClasses(@TempDir Path tmpRepo) throws IOException {
        // No agent-service/target/classes; default mode must fail.
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("code-symbols.json");
        IOException ex = assertThrows(IOException.class,
                () -> CodeSymbolFactExtractor.extract(ctx, out));
        assertTrue(ex.getMessage().contains("missing target/classes"),
                "Failure message must call out the missing target/classes condition (P2-3).");
    }

    @Test
    void allowMissingClassesEmitsEmptyFactSet(@TempDir Path tmpRepo) throws IOException {
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("code-symbols.json");
        CodeSymbolFactExtractor.extract(ctx, out, true);
        assertTrue(Files.exists(out), "Even with no modules, the fact file must be written (banner + empty facts).");
        String body = Files.readString(out);
        assertTrue(body.contains("\"facts\""), "Body must declare a facts array");
        assertTrue(body.contains("DO NOT EDIT"), "Body must carry the LLM-no-author banner");
    }

    @Test
    void testInventoryAllowMissingEmitsEmptyFactSet(@TempDir Path tmpRepo) throws IOException {
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("tests.json");
        TestInventoryFactExtractor.extract(ctx, out, true);
        assertTrue(Files.exists(out));
    }

    @Test
    void testInventoryFailsClosedWhenUnbuilt(@TempDir Path tmpRepo) throws IOException {
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("tests.json");
        IOException ex = assertThrows(IOException.class,
                () -> TestInventoryFactExtractor.extract(ctx, out));
        assertTrue(ex.getMessage().contains("not built"),
                "Failure message must distinguish unbuilt module from legitimately-empty test surface (P2-3).");
    }
}
