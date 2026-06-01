package com.huawei.ascend.tools.architecture.facts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wave-2+ unified CLI for the fact-layer extractors (Rule G-15 / ADR-0154).
 *
 * <p>Usage (invoked from Maven): {@code mvn -f tools/architecture-workspace/pom.xml
 * exec:java@extract-facts}.
 *
 * <p>Arguments (all optional):
 * <ul>
 *     <li>{@code --repo &lt;path&gt;} — repository root. Defaults to current
 *         working directory.</li>
 *     <li>{@code --out &lt;dir&gt;} — output directory under the repo for
 *         emitted fact files. Defaults to
 *         {@code architecture/facts/generated}.</li>
 *     <li>{@code --extractor-version &lt;tag&gt;} — semver / build tag of
 *         the extractor module. Defaults to {@code 0.1.0-SNAPSHOT}
 *         (matches the Maven module version).</li>
 *     <li>{@code --check} — render in-memory and diff against the
 *         committed file; exits non-zero on mismatch (Rule G-15.c forward
 *         contract).</li>
 * </ul>
 *
 * <p>Wave 2 ships three extractors: ModuleBuildFactExtractor,
 * AdrFactExtractor, RuntimeConfigFactExtractor. Wave 3+ adds
 * ContractFactExtractor; Wave 4 adds CodeSymbolFactExtractor and
 * TestInventoryFactExtractor.
 */
public final class ExtractFactsCli {

    private ExtractFactsCli() {
    }

    public static void main(String[] args) throws IOException {
        String repoArg = argValue(args, "--repo", ".");
        String outArg = argValue(args, "--out", "architecture/facts/generated");
        String version = argValue(args, "--extractor-version", "0.1.0-SNAPSHOT");
        boolean check = hasFlag(args, "--check");
        boolean allowMissingClasses = hasFlag(args, "--allow-missing-classes");

        Path repo = Path.of(repoArg).toAbsolutePath().normalize();
        Path outDir = repo.resolve(outArg);
        Files.createDirectories(outDir);

        ExtractorContext ctx = new ExtractorContext(repo, version);

        // Run each extractor; in --check mode write to temp files and diff.
        run(ctx, outDir.resolve("module-build.json"), check, ModuleBuildFactExtractor::extract);
        run(ctx, outDir.resolve("adrs.json"), check, AdrFactExtractor::extract);
        run(ctx, outDir.resolve("runtime-config.json"), check, RuntimeConfigFactExtractor::extract);
        run(ctx, outDir.resolve("contract-surfaces.json"), check, ContractFactExtractor::extract);
        final boolean amc = allowMissingClasses;
        run(ctx, outDir.resolve("code-symbols.json"), check,
                (c, out) -> CodeSymbolFactExtractor.extract(c, out, amc));
        run(ctx, outDir.resolve("tests.json"), check,
                (c, out) -> TestInventoryFactExtractor.extract(c, out, amc));

        System.out.println("ExtractFactsCli: ok (commit=" + ctx.repoCommit() + ", out=" + outDir + ")");
    }

    @FunctionalInterface
    private interface Extractor {
        void run(ExtractorContext ctx, Path output) throws IOException;
    }

    private static void run(ExtractorContext ctx, Path target, boolean check, Extractor extractor) throws IOException {
        if (!check) {
            extractor.run(ctx, target);
            return;
        }
        Path tmp = Files.createTempFile("fact-extract-", ".json");
        try {
            extractor.run(ctx, tmp);
            if (!Files.exists(target)) {
                throw new IOException("--check: committed file missing " + target);
            }
            String expected = normalizeProvenance(Files.readString(target));
            String actual = normalizeProvenance(Files.readString(tmp));
            if (!expected.equals(actual)) {
                throw new IOException("--check: content drift detected on " + target
                        + " (differs after normalizing per-commit provenance SHAs)");
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Normalize the per-commit provenance that legitimately varies between the
     * commit a fact file was generated in and any later workspace HEAD: the
     * 40-char {@code repo_commit} SHA (and the banner's "at commit &lt;sha&gt;").
     * Without this, the byte-identity check (Rule G-15.c.bytes) could never be
     * satisfied across commits — a file generated in commit P embeds P's SHA,
     * but a re-extraction at HEAD=C embeds C's SHA. Replacing every 40-hex run
     * with a placeholder makes the check compare CONTENT only; genuine content
     * drift (a hand-edited fact value) is still detected.
     */
    private static String normalizeProvenance(String json) {
        return json.replaceAll("[0-9a-f]{40}", "<commit>");
    }

    private static String argValue(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
