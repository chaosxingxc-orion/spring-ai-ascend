package com.huawei.ascend.tools.architecture.facts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Per-extractor-run context — pins repository root, the workspace HEAD
 * commit, and the extractor-binary version so each emitted fact carries
 * stable provenance (Rule G-15.b).
 *
 * <p>{@code repoCommit} is read at construction time from the git plumbing
 * inside {@code .git/HEAD}. If the workspace isn't a git checkout (e.g.,
 * test fixture), a deterministic fallback of "0" * 40 is used; the gate
 * accepts any 40-char lowercase hex string, so the fallback keeps the
 * schema happy without falsely claiming a real commit.
 */
public final class ExtractorContext {

    private static final String FALLBACK_COMMIT = "0000000000000000000000000000000000000000";

    private final Path repoRoot;
    private final String repoCommit;
    private final String extractorVersion;

    public ExtractorContext(Path repoRoot, String extractorVersion) throws IOException {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.extractorVersion = extractorVersion;
        this.repoCommit = readRepoCommit(this.repoRoot);
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public String repoCommit() {
        return repoCommit;
    }

    public String extractorVersion() {
        return extractorVersion;
    }

    /**
     * Resolve a workspace path under the repo root.
     */
    public Path resolve(String relPath) {
        return repoRoot.resolve(relPath);
    }

    private static String readRepoCommit(Path repoRoot) throws IOException {
        Path headFile = repoRoot.resolve(".git").resolve("HEAD");
        if (!Files.isRegularFile(headFile)) {
            return FALLBACK_COMMIT;
        }
        String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
        if (head.startsWith("ref: ")) {
            Path refFile = repoRoot.resolve(".git").resolve(head.substring(5).trim());
            if (Files.isRegularFile(refFile)) {
                String sha = Files.readString(refFile, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if (sha.matches("^[0-9a-f]{40}$")) {
                    return sha;
                }
            }
            // packed-refs fallback — minimal parser for the single-line entry case.
            Path packedRefs = repoRoot.resolve(".git").resolve("packed-refs");
            if (Files.isRegularFile(packedRefs)) {
                String target = head.substring(5).trim();
                for (String line : Files.readAllLines(packedRefs, StandardCharsets.UTF_8)) {
                    if (line.startsWith("#") || line.startsWith("^")) {
                        continue;
                    }
                    int sp = line.indexOf(' ');
                    if (sp <= 0) {
                        continue;
                    }
                    String sha = line.substring(0, sp).toLowerCase(Locale.ROOT);
                    String ref = line.substring(sp + 1).trim();
                    if (target.equals(ref) && sha.matches("^[0-9a-f]{40}$")) {
                        return sha;
                    }
                }
            }
            return FALLBACK_COMMIT;
        }
        String detached = head.toLowerCase(Locale.ROOT);
        if (detached.matches("^[0-9a-f]{40}$")) {
            return detached;
        }
        return FALLBACK_COMMIT;
    }
}
