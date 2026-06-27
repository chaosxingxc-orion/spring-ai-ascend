package com.huawei.ascend.examples.workmate.taskstarter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateGithubProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateWorkspaceProperties;
import com.huawei.ascend.examples.workmate.connector.ConnectorCredentialStore;
import com.huawei.ascend.examples.workmate.session.WorkspaceService;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRepoServiceTest {

    @TempDir
    Path tempDir;

    private GitRepoService service;
    private Path repoPath;

    @BeforeEach
    void setUp() throws Exception {
        Path workspaceBase = tempDir.resolve("workspaces");
        Files.createDirectories(workspaceBase);
        repoPath = workspaceBase.resolve("presets/demo-repo");
        Files.createDirectories(repoPath);
        initRepo(repoPath);

        WorkspaceService workspaceService =
                new WorkspaceService(new WorkmateWorkspaceProperties(workspaceBase.toString(), List.of()));
        ConnectorCredentialStore credentialStore = new ConnectorCredentialStore(
                new WorkmateDataProperties(tempDir.resolve("data").toString()), new ObjectMapper());
        service = new GitRepoService(
                workspaceService,
                new GithubCredentialResolver(credentialStore, new WorkmateGithubProperties("", "https://api.github.com")),
                new GithubApiClient(new WorkmateGithubProperties("", "https://api.github.com"), new ObjectMapper()),
                new GitCommandRunner());
    }

    @Test
    void listsLocalReposAndBranches() {
        assertThat(service.listLocalRepos())
                .anyMatch(repo -> repo.path().equals("presets/demo-repo"));

        assertThat(service.listBranches("presets/demo-repo"))
                .extracting(branch -> branch.name())
                .isNotEmpty();
    }

    @Test
    void checksOutBranchWhenPresent() throws Exception {
        runGit(repoPath, "checkout", "-b", "feature/test");
        Files.writeString(repoPath.resolve("feature.txt"), "feature");
        runGit(repoPath, "add", "feature.txt");
        runGit(repoPath, "commit", "-m", "feature");
        runGit(repoPath, "checkout", "-");

        service.checkoutBranch("presets/demo-repo", "feature/test");
        assertThat(Files.exists(repoPath.resolve("feature.txt"))).isTrue();
    }

    @Test
    void rejectsFlagLikeBranchNames() {
        assertThatThrownBy(() -> service.checkoutBranch("presets/demo-repo", "-B attacker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid git branch");
    }

    @Test
    void redactsGithubTokenFromGitErrors() throws Exception {
        String token = "sk-test-token-that-should-not-leak";
        Method safeGitCommand = GitCommandRunner.class.getDeclaredMethod("safeGitCommand", List.class, String.class);
        safeGitCommand.setAccessible(true);

        String command = (String) safeGitCommand.invoke(
                null,
                List.of("clone", "https://x-access-token:" + token + "@github.com/example/repo.git"),
                token);

        assertThat(command).doesNotContain(token).contains("[redacted]");
    }

    private static void initRepo(Path repo) throws Exception {
        runGit(repo, "init");
        runGit(repo, "config", "user.email", "test@example.com");
        runGit(repo, "config", "user.name", "Test");
        Files.writeString(repo.resolve("README.md"), "hello");
        runGit(repo, "add", "README.md");
        runGit(repo, "commit", "-m", "init");
    }

    private static void runGit(Path cwd, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(buildGitCommand(args));
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
    }

    private static List<String> buildGitCommand(String... args) {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }
}
