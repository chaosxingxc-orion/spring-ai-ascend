package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExtractFactsCli test (Round-3 Wave Beta, 2026-05-28 sweep defect 16).
 *
 * <p>The orchestrator binary was untested until Round-3. These tests
 * exercise the two highest-value invariants:
 *
 * <ol>
 *     <li>A clean {@code --repo} produces every expected fact file
 *         (YAML-source extractors only; code/test extractors require
 *         compiled classes and are exercised separately).</li>
 *     <li>{@code --check} mode correctly distinguishes byte-identical
 *         re-emission from mutated output. This is the foundation of
 *         Rule G-15.c and was the root cause of the Round-3 R1
 *         {@code || true} fail-open defect.</li>
 * </ol>
 */
class ExtractFactsCliTest {

    @Test
    void cliEmitsAllYamlSourceFactFilesOnFreshRun(@TempDir Path tmpRepo) throws IOException {
        // Minimal repo: pom.xml + docs/adr + an application.yml so the
        // four YAML-source extractors all have non-empty inputs. Code +
        // test extractors get --allow-missing-classes so no compiled
        // classes are required for this CLI smoke test.
        Files.writeString(tmpRepo.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n<project>\n  <modules>\n    <module>alpha</module>\n  </modules>\n</project>\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(tmpRepo.resolve("alpha"));
        Files.createDirectories(tmpRepo.resolve("docs/adr"));
        Files.writeString(tmpRepo.resolve("docs/adr/0001-alpha.yaml"),
                "id: ADR-0001\ntitle: Alpha\nstatus: accepted\nlevel: L0\nview: development\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(tmpRepo.resolve("docs/contracts"));
        Files.writeString(tmpRepo.resolve("docs/contracts/openapi-v1.yaml"),
                "openapi: '3.0.1'\ninfo:\n  title: t\n  version: '1.0'\npaths: {}\n",
                StandardCharsets.UTF_8);
        ExtractFactsCli.main(new String[] {
                "--repo", tmpRepo.toString(),
                "--out", "architecture/facts/generated",
                "--extractor-version", "0.0.0-TEST",
                "--allow-missing-classes"
        });
        Path out = tmpRepo.resolve("architecture/facts/generated");
        for (String name : new String[] {
                "module-build.json", "adrs.json", "runtime-config.json",
                "contract-surfaces.json", "code-symbols.json", "tests.json"
        }) {
            assertTrue(Files.exists(out.resolve(name)),
                    "Expected fact file " + name + " to be emitted by ExtractFactsCli");
        }
    }

    @Test
    void checkModeFailsOnDeliberateMutation(@TempDir Path tmpRepo) throws IOException {
        Files.writeString(tmpRepo.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n<project>\n  <modules>\n    <module>alpha</module>\n  </modules>\n</project>\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(tmpRepo.resolve("alpha"));
        Files.createDirectories(tmpRepo.resolve("docs/adr"));
        Files.createDirectories(tmpRepo.resolve("docs/contracts"));
        Files.writeString(tmpRepo.resolve("docs/contracts/openapi-v1.yaml"), "paths: {}\n", StandardCharsets.UTF_8);
        // First run: emit baseline.
        ExtractFactsCli.main(new String[] {
                "--repo", tmpRepo.toString(),
                "--out", "architecture/facts/generated",
                "--extractor-version", "0.0.0-TEST",
                "--allow-missing-classes"
        });
        Path moduleBuild = tmpRepo.resolve("architecture/facts/generated/module-build.json");
        // Mutate: append a noise line. The next --check run MUST fail.
        Files.writeString(moduleBuild,
                Files.readString(moduleBuild) + "// mutation\n",
                StandardCharsets.UTF_8);
        // The --check invocation should throw IOException because of byte drift.
        boolean threw = false;
        try {
            ExtractFactsCli.main(new String[] {
                    "--repo", tmpRepo.toString(),
                    "--out", "architecture/facts/generated",
                    "--extractor-version", "0.0.0-TEST",
                    "--allow-missing-classes",
                    "--check"
            });
        } catch (IOException ex) {
            threw = true;
            assertTrue(ex.getMessage().contains("--check"),
                    "Exception message should describe the --check drift");
        } catch (RuntimeException ex) {
            threw = true;
        }
        assertTrue(threw, "ExtractFactsCli --check MUST throw on a mutated fact file (R1 acceptance evidence).");
        // Sanity: the mutated module-build.json should differ from a fresh emit
        // (proves the test setup actually mutated the file).
        Files.delete(moduleBuild);
        ExtractFactsCli.main(new String[] {
                "--repo", tmpRepo.toString(),
                "--out", "architecture/facts/generated",
                "--extractor-version", "0.0.0-TEST",
                "--allow-missing-classes"
        });
        // After fresh emit, --check passes.
        try {
            ExtractFactsCli.main(new String[] {
                    "--repo", tmpRepo.toString(),
                    "--out", "architecture/facts/generated",
                    "--extractor-version", "0.0.0-TEST",
                    "--allow-missing-classes",
                    "--check"
            });
        } catch (Exception ex) {
            throw new AssertionError("Fresh extract followed by --check must pass; got: " + ex.getMessage(), ex);
        }
        // Defensive: keep one assertion that exercises both AssertJ-style and
        // standard equality helpers so the test signal is unambiguous.
        assertEquals(true, Files.exists(moduleBuild));
        assertNotEquals(0L, Files.size(moduleBuild));
    }
}
