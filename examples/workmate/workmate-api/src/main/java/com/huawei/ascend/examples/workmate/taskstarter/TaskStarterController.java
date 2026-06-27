package com.huawei.ascend.examples.workmate.taskstarter;

import com.huawei.ascend.examples.workmate.taskstarter.dto.CloneGithubRepoRequest;
import com.huawei.ascend.examples.workmate.taskstarter.dto.CloneGithubRepoResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GitBranchResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GithubRepoResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GithubStatusResponse;
import com.huawei.ascend.examples.workmate.taskstarter.dto.LocalGitRepoResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/task-starter")
public class TaskStarterController {

    private final GitRepoService gitRepoService;

    public TaskStarterController(GitRepoService gitRepoService) {
        this.gitRepoService = gitRepoService;
    }

    @GetMapping("/github/status")
    public GithubStatusResponse githubStatus() {
        return gitRepoService.githubStatus();
    }

    @GetMapping("/git/local-repos")
    public List<LocalGitRepoResponse> localRepos() {
        return gitRepoService.listLocalRepos();
    }

    @GetMapping("/git/branches")
    public List<GitBranchResponse> branches(@RequestParam String path) {
        return gitRepoService.listBranches(path);
    }

    @GetMapping("/github/repos")
    public List<GithubRepoResponse> githubRepos(@RequestParam(required = false) String q) {
        return gitRepoService.searchGithubRepos(q);
    }

    @GetMapping("/github/repos/{owner}/{repo}/branches")
    public List<GitBranchResponse> githubBranches(@PathVariable String owner, @PathVariable String repo) {
        return gitRepoService.listGithubBranches(owner, repo);
    }

    @PostMapping("/github/clone")
    public CloneGithubRepoResponse cloneGithub(@Valid @RequestBody CloneGithubRepoRequest request) {
        return gitRepoService.cloneGithubRepo(request.owner(), request.repo(), request.branch());
    }
}
