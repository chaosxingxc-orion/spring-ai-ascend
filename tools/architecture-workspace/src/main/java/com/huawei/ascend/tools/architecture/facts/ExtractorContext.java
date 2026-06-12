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
 * inside the resolved git dir's {@code HEAD}. Linked worktrees (where
 * {@code .git} is a file carrying a {@code gitdir:} pointer, and shared refs
 * live in the {@code commondir}) are followed. If the workspace isn't a git
 * checkout (e.g., test fixture), a deterministic fallback of "0" * 40 is
 * used; the gate accepts any 40-char lowercase hex string, so the fallback
 * keeps the schema happy without falsely claiming a real commit.
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
        Path gitDir = resolveGitDir(repoRoot);
        if (gitDir == null) {
            return FALLBACK_COMMIT;
        }
        Path headFile = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(headFile)) {
            return FALLBACK_COMMIT;
        }
        String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
        if (head.startsWith("ref: ")) {
            // Shared refs (refs/heads/*, packed-refs) live in the common dir; in a
            // plain checkout that IS the git dir, in a linked worktree it is the
            // directory named by the gitdir's `commondir` file.
            Path commonDir = resolveCommonDir(gitDir);
            Path refFile = commonDir.resolve(head.substring(5).trim());
            if (Files.isRegularFile(refFile)) {
                String sha = Files.readString(refFile, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if (sha.matches("^[0-9a-f]{40}$")) {
                    return sha;
                }
            }
            // packed-refs fallback — minimal parser for the single-line entry case.
            Path packedRefs = commonDir.resolve("packed-refs");
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

    /**
     * A plain checkout has a {@code .git} directory; a linked worktree has a
     * {@code .git} FILE whose single line points at the per-worktree git dir.
     */
    private static Path resolveGitDir(Path repoRoot) throws IOException {
        Path dotGit = repoRoot.resolve(".git");
        if (Files.isDirectory(dotGit)) {
            return dotGit;
        }
        if (!Files.isRegularFile(dotGit)) {
            return null;
        }
        String content = Files.readString(dotGit, StandardCharsets.UTF_8).trim();
        if (!content.startsWith("gitdir:")) {
            return null;
        }
        Path pointed = Path.of(content.substring("gitdir:".length()).trim());
        Path gitDir = pointed.isAbsolute() ? pointed : repoRoot.resolve(pointed).normalize();
        return Files.isDirectory(gitDir) ? gitDir : null;
    }

    private static Path resolveCommonDir(Path gitDir) throws IOException {
        Path commonDirFile = gitDir.resolve("commondir");
        if (!Files.isRegularFile(commonDirFile)) {
            return gitDir;
        }
        Path pointed = Path.of(Files.readString(commonDirFile, StandardCharsets.UTF_8).trim());
        Path commonDir = pointed.isAbsolute() ? pointed : gitDir.resolve(pointed).normalize();
        return Files.isDirectory(commonDir) ? commonDir : gitDir;
    }
}
