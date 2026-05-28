package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModuleBuildFactExtractor test (Round-2 Wave C, 2026-05-28 P2-2).
 */
class ModuleBuildFactExtractorTest {

    @Test
    void emitsOneFactPerDeclaredModule(@TempDir Path tmpRepo) throws IOException {
        Files.writeString(tmpRepo.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n"
                        + "<project>\n"
                        + "  <modules>\n"
                        + "    <module>alpha</module>\n"
                        + "    <module>beta</module>\n"
                        + "  </modules>\n"
                        + "</project>\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(tmpRepo.resolve("alpha"));
        Files.writeString(tmpRepo.resolve("alpha/module-metadata.yaml"),
                "kind: domain\nversion: 0.1.0\nspi_packages:\n  - com.example.alpha.spi\n",
                StandardCharsets.UTF_8);
        // Beta has no module-metadata.yaml — fact must still be emitted with present=false.
        Files.createDirectories(tmpRepo.resolve("beta"));
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("module-build.json");
        ModuleBuildFactExtractor.extract(ctx, out);
        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("build-module/alpha"));
        assertTrue(body.contains("build-module/beta"));
        assertTrue(body.contains("\"kind\" : \"domain\""), "alpha metadata must be inlined into observed_value");
        assertTrue(body.contains("\"module_metadata_present\" : false"),
                "Missing module-metadata.yaml must surface as present=false rather than dropping the module.");
    }
}
