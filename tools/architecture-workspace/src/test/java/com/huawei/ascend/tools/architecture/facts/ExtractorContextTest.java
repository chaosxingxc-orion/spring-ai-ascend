package com.huawei.ascend.tools.architecture.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Provenance resolution for the three checkout shapes an extractor runs in:
 * a plain {@code .git} directory, a linked worktree ({@code .git} file with a
 * {@code gitdir:} pointer and shared refs behind {@code commondir}), and a
 * non-git tree (deterministic all-zero fallback).
 */
class ExtractorContextTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String ZEROS = "0000000000000000000000000000000000000000";

    @Test
    void readsHeadThroughRefFileInPlainCheckout(@TempDir Path tmp) throws IOException {
        Path gitDir = Files.createDirectories(tmp.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
        Path refDir = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.writeString(refDir.resolve("main"), SHA + "\n", StandardCharsets.UTF_8);

        ExtractorContext ctx = new ExtractorContext(tmp, "0.0.0-TEST");
        assertEquals(SHA, ctx.repoCommit());
    }

    @Test
    void followsGitdirPointerAndCommondirInLinkedWorktree(@TempDir Path tmp) throws IOException {
        // Layout: repo/.git (dir, holds the shared refs) and wt/.git (file
        // pointing at repo/.git/worktrees/wt, whose commondir points back up).
        Path mainGit = Files.createDirectories(tmp.resolve("repo").resolve(".git"));
        Path refDir = Files.createDirectories(mainGit.resolve("refs").resolve("heads"));
        Files.writeString(refDir.resolve("feature"), SHA + "\n", StandardCharsets.UTF_8);

        Path wtGitDir = Files.createDirectories(mainGit.resolve("worktrees").resolve("wt"));
        Files.writeString(wtGitDir.resolve("HEAD"), "ref: refs/heads/feature\n", StandardCharsets.UTF_8);
        Files.writeString(wtGitDir.resolve("commondir"), "../..\n", StandardCharsets.UTF_8);

        Path worktree = Files.createDirectories(tmp.resolve("wt"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + wtGitDir + "\n", StandardCharsets.UTF_8);

        ExtractorContext ctx = new ExtractorContext(worktree, "0.0.0-TEST");
        assertEquals(SHA, ctx.repoCommit());
    }

    @Test
    void fallsBackToZerosOutsideAnyGitCheckout(@TempDir Path tmp) throws IOException {
        ExtractorContext ctx = new ExtractorContext(tmp, "0.0.0-TEST");
        assertEquals(ZEROS, ctx.repoCommit());
    }
}
