package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuntimeConfigFactExtractor test (Round-3 Wave Beta, 2026-05-28 sweep
 * defect 15). Verifies one fact emitted per top-level key in
 * {@code <module>/src/main/resources/application*.yml}.
 */
class RuntimeConfigFactExtractorTest {

    @Test
    void emitsOneFactPerTopLevelConfigKey(@TempDir Path tmpRepo) throws IOException {
        Path agentService = Files.createDirectories(tmpRepo.resolve("agent-service/src/main/resources"));
        Files.writeString(agentService.resolve("application.yml"),
                "spring:\n"
                        + "  application:\n"
                        + "    name: test\n"
                        + "logging:\n"
                        + "  level:\n"
                        + "    root: INFO\n"
                        + "app:\n"
                        + "  posture: dev\n",
                StandardCharsets.UTF_8);
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        Path out = tmpRepo.resolve("runtime-config.json");
        RuntimeConfigFactExtractor.extract(ctx, out);
        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("runtime-config/agent-service/application-yml/spring"));
        assertTrue(body.contains("runtime-config/agent-service/application-yml/logging"));
        assertTrue(body.contains("runtime-config/agent-service/application-yml/app"));
        assertTrue(body.contains("\"top_level_key\" : \"app\""));
    }
}
