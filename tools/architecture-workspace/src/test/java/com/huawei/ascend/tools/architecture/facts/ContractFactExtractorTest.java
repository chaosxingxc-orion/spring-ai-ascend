package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContractFactExtractor tests (Round-2 Wave C, 2026-05-28 P2-2).
 *
 * <p>Pinned behaviour:
 * <ul>
 *     <li>parses an OpenAPI fixture into HTTP operation facts;</li>
 *     <li>fails closed on a malformed contract YAML (closes P1-3);</li>
 *     <li>tolerates explicitly exempted basenames via
 *         {@code docs/contracts/parse-exempt.txt}.</li>
 * </ul>
 */
class ContractFactExtractorTest {

    @Test
    void emitsHttpOperationFactFromOpenApiFixture(@TempDir Path tmpRepo) throws IOException {
        Path contracts = Files.createDirectories(tmpRepo.resolve("docs/contracts"));
        Files.writeString(contracts.resolve("openapi-v1.yaml"),
                "openapi: '3.0.1'\n"
                        + "info:\n"
                        + "  title: t\n"
                        + "  version: '1.0'\n"
                        + "paths:\n"
                        + "  /v1/example:\n"
                        + "    get:\n"
                        + "      operationId: getExample\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: ok\n",
                StandardCharsets.UTF_8);
        Path out = tmpRepo.resolve("facts.json");
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        ContractFactExtractor.extract(ctx, out);
        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("contract-op/getexample"), "Expected getExample operation fact");
        assertTrue(body.contains("/v1/example"), "Expected request path in observed_value");
    }

    @Test
    void failsClosedOnMalformedContractYaml(@TempDir Path tmpRepo) throws IOException {
        Path contracts = Files.createDirectories(tmpRepo.resolve("docs/contracts"));
        // Empty openapi keeps the OpenAPI branch a no-op.
        Files.writeString(contracts.resolve("openapi-v1.yaml"), "paths: {}\n", StandardCharsets.UTF_8);
        // Malformed: unquoted prose with a literal colon inside a scalar value
        // is the exact failure mode that ran-event.v1.yaml exhibited before
        // the Round-2 Wave B fix.
        Files.writeString(contracts.resolve("broken.v1.yaml"),
                "schema: broken/v1\n"
                        + "promotion_trigger: at point: this fails to parse\n",
                StandardCharsets.UTF_8);
        Path out = tmpRepo.resolve("facts.json");
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        IOException ex = assertThrows(IOException.class,
                () -> ContractFactExtractor.extract(ctx, out));
        assertTrue(ex.getMessage().contains("broken.v1.yaml"),
                "Failure message must name the offending contract file (Round-2 P1-3).");
        // Confirm no parse_failed fact was emitted (the Round-1 swallow is gone).
        assertFalse(Files.exists(out) && Files.readString(out, StandardCharsets.UTF_8).contains("parse_failed"),
                "ContractFactExtractor must not emit parse_failed: true stubs for active contracts.");
    }

    @Test
    void allowsExemptedBasename(@TempDir Path tmpRepo) throws IOException {
        Path contracts = Files.createDirectories(tmpRepo.resolve("docs/contracts"));
        Files.writeString(contracts.resolve("openapi-v1.yaml"), "paths: {}\n", StandardCharsets.UTF_8);
        Files.writeString(contracts.resolve("broken.v1.yaml"),
                "schema: broken/v1\n"
                        + "promotion_trigger: at point: this fails to parse\n",
                StandardCharsets.UTF_8);
        Files.writeString(contracts.resolve("parse-exempt.txt"),
                "broken.v1.yaml\n",
                StandardCharsets.UTF_8);
        Path out = tmpRepo.resolve("facts.json");
        ExtractorContext ctx = new ExtractorContext(tmpRepo, "0.0.0-TEST");
        ContractFactExtractor.extract(ctx, out);
        String body = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"exempt\" : true"),
                "Exempted parse failures must be recorded as exempt=true facts (P1-3 escape hatch).");
    }
}
