package com.huawei.ascend.examples.workmate.taskstarter;

import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import com.huawei.ascend.examples.workmate.session.WorkspaceService;
import com.huawei.ascend.examples.workmate.taskstarter.dto.CloneGithubRepoResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GitBranchResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GithubRepoResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GithubStatusResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.LocalGitRepoResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GitRepoService {

    private static final int MAX_SCAN_DEPTH = 4;

    private final WorkspaceService workspaceService;
    private final GithubCredentialResolver credentialResolver;
    private final GithubApiClient githubApiClient;
    private final GitCommandRunner gitCommandRunner;

    public GitRepoService(
            WorkspaceService workspaceService,
            GithubCredentialResolver credentialResolver,
            GithubApiClient githubApiClient,
            GitCommandRunner gitCommandRunner) {
        this.workspaceService = workspaceService;
        this.credentialResolver = credentialResolver;
        this.githubApiClient = githubApiClient;
        this.gitCommandRunner = gitCommandRunner;
    }

    public GithubStatusResponse githubStatus() {
        Optional<String> token = credentialResolver.resolveToken();
        if (token.isPresent()) {
            return new GithubStatusResponse(true, "GitHub token configured");
        }
        return new GithubStatusResponse(false, "Configure WORKMATE_GITHUB_TOKEN or connector github token");
    }

    public List<LocalGitRepoResponse> listLocalRepos() {
        Path base = workspaceService.basePath();
        Map<String, LocalGitRepoResponse> repos = new LinkedHashMap<>();
        scanGitRepos(base, base, 0, repos);
        return repos.values().stream()
                .sorted(Comparator.comparing(LocalGitRepoResponse::path))
                .toList();
    }

    public List<GitBranchResponse> listBranches(String relativePath) {
        Path repo = resolveRepoRoot(relativePath);
        String current = currentBranch(repo).orElse("");
        String token = credentialResolver.resolveToken().orElse(null);
        List<String> branches = gitCommandRunner.runLines(repo, List.of("branch", "--format=%(refname:short)"), false, token);
        return branches.stream()
                .filter(name -> !name.isBlank())
                .distinct()
                .map(name -> new GitBranchResponse(name, name.equals(current)))
                .toList();
    }

    public void checkoutBranch(String relativePath, String branch) {
        if (branch == null || branch.isBlank()) {
            return;
        }
        Path repo = resolveRepoRoot(relativePath);
        String token = credentialResolver.resolveToken().orElse(null);
        gitCommandRunner.run(repo, List.of("checkout", GitCommandRunner.requireSafeBranch(branch.trim())), false, token);
    }

    public List<GithubRepoResponse> searchGithubRepos(String query) {
        Optional<String> token = credentialResolver.resolveToken();
        if (token.isEmpty()) {
            return List.of();
        }
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isBlank()) {
            return githubApiClient.listUserRepos(token.get());
        }
        return githubApiClient.searchRepos(trimmed, token.get());
    }

    public List<GitBranchResponse> listGithubBranches(String owner, String repo) {
        Optional<String> token = credentialResolver.resolveToken();
        if (token.isEmpty()) {
            return List.of();
        }
        return githubApiClient.listBranches(owner, repo, token.get()).stream()
                .map(item -> new GitBranchResponse(String.valueOf(item.get("name")), false))
                .toList();
    }

    public CloneGithubRepoResponse cloneGithubRepo(String owner, String repo, String branch) {
        Optional<String> token = credentialResolver.resolveToken();
        if (token.isEmpty()) {
            throw new IllegalStateException("GitHub token not configured");
        }
        String safeOwner = GithubApiClient.sanitizeSegment(owner);
        String safeRepo = GithubApiClient.sanitizeSegment(repo);
        String relativePath = "repos/" + safeOwner + "-" + safeRepo;
        Path target = workspaceService.basePath().resolve(relativePath).normalize();
        workspaceService.validateWithinBase(target);

        String resolvedBranch = branch;
        if (resolvedBranch == null || resolvedBranch.isBlank()) {
            resolvedBranch = githubApiClient.defaultBranch(safeOwner, safeRepo, token.get());
        }
        resolvedBranch = GitCommandRunner.requireSafeBranch(resolvedBranch);

        boolean cloned = false;
        if (Files.isDirectory(target.resolve(".git"))) {
            gitCommandRunner.run(target, List.of("fetch", "--all", "--prune"), true, token.get());
            gitCommandRunner.run(target, List.of("checkout", resolvedBranch), false, token.get());
            gitCommandRunner.run(target, List.of("pull", "--ff-only"), true, token.get());
        } else {
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create repo directory " + target.getParent(), ex);
            }
            String cloneUrl = "https://x-access-token@github.com/" + safeOwner + "/" + safeRepo + ".git";
            gitCommandRunner.run(
                    target.getParent(),
                    List.of(
                            "clone",
                            "--branch",
                            resolvedBranch,
                            "--single-branch",
                            cloneUrl,
                            target.getFileName().toString()),
                    true,
                    token.get());
            cloned = true;
        }
        return new CloneGithubRepoResponse(relativePath.replace('\\', '/'), resolvedBranch, cloned);
    }

    private Path resolveRepoRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("repo path required");
        }
        Path repo = workspaceService.basePath().resolve(relativePath).normalize();
        workspaceService.validateWithinBase(repo);
        if (!Files.isDirectory(repo.resolve(".git"))) {
            throw new IllegalArgumentException("Not a git repository: " + relativePath);
        }
        return repo;
    }

    private void scanGitRepos(Path base, Path current, int depth, Map<String, LocalGitRepoResponse> repos) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        if (Files.isDirectory(current.resolve(".git"))) {
            String relative = base.relativize(current).toString().replace('\\', '/');
            if (!relative.isBlank() && !isSessionUuidDir(relative)) {
                repos.putIfAbsent(
                        relative,
                        new LocalGitRepoResponse(
                                relative,
                                current.getFileName().toString(),
                                currentBranch(current).orElse("")));
            }
            return;
        }
        if (!Files.isDirectory(current)) {
            return;
        }
        try (var stream = Files.list(current)) {
            for (Path child : stream.toList()) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (name.equals(".git") || name.startsWith(".")) {
                    continue;
                }
                if (depth == 0 && isSessionUuidDir(name)) {
                    continue;
                }
                scanGitRepos(base, child, depth + 1, repos);
            }
        } catch (IOException ex) {
            throw new WorkspaceException("Failed to scan git repos under " + current, ex);
        }
    }

    private static boolean isSessionUuidDir(String name) {
        try {
            UUID.fromString(name);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Optional<String> currentBranch(Path repo) {
        String token = credentialResolver.resolveToken().orElse(null);
        List<String> lines = gitCommandRunner.runLines(repo, List.of("rev-parse", "--abbrev-ref", "HEAD"), false, token);
        return lines.stream().findFirst().filter(line -> !line.isBlank());
    }
}
