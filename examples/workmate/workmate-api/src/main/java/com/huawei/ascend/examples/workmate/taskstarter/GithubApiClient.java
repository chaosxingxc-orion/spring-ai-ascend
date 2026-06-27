package com.huawei.ascend.examples.workmate.taskstarter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateGithubProperties;
import com.huawei.ascend.examples.workmate.taskstarter.dto.GithubRepoResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GithubApiClient {

    private final WorkmateGithubProperties githubProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GithubApiClient(WorkmateGithubProperties githubProperties, ObjectMapper objectMapper) {
        this.githubProperties = githubProperties;
        this.objectMapper = objectMapper;
    }

    public List<GithubRepoResponse> listUserRepos(String token) {
        return getList("/user/repos?per_page=30&sort=updated", token).stream()
                .map(this::toGithubRepo)
                .toList();
    }

    public List<GithubRepoResponse> searchRepos(String query, String token) {
        Map<String, Object> payload = getObject(
                "/search/repositories?q=" + encodeQuery(query + " in:name") + "&per_page=30", token);
        return readList(payload.get("items")).stream().map(this::toGithubRepo).toList();
    }

    public List<Map<String, Object>> listBranches(String owner, String repo, String token) {
        return getList("/repos/" + encode(owner) + "/" + encode(repo) + "/branches?per_page=100", token);
    }

    public String defaultBranch(String owner, String repo, String token) {
        Map<String, Object> payload = getObject("/repos/" + encode(owner) + "/" + encode(repo), token);
        Object value = payload.get("default_branch");
        return value == null ? "main" : String.valueOf(value);
    }

    public GithubRepoResponse toGithubRepo(Map<String, Object> item) {
        Map<String, Object> owner = readMap(item.get("owner"));
        String ownerLogin = owner.get("login") == null ? "" : String.valueOf(owner.get("login"));
        String name = String.valueOf(item.getOrDefault("name", ""));
        return new GithubRepoResponse(
                ownerLogin,
                name,
                String.valueOf(item.getOrDefault("full_name", ownerLogin + "/" + name)),
                String.valueOf(item.getOrDefault("default_branch", "main")),
                String.valueOf(item.getOrDefault("clone_url", "")));
    }

    static String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("owner/repo required");
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid git segment: " + value);
        }
        return trimmed;
    }

    private Map<String, Object> getObject(String path, String token) {
        String body = request(path, token);
        try {
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse GitHub response", ex);
        }
    }

    private List<Map<String, Object>> getList(String path, String token) {
        String body = request(path, token);
        try {
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse GitHub response", ex);
        }
    }

    private String request(String path, String token) {
        try {
            URI uri = URI.create(githubProperties.apiBase() + path);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("GitHub API " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("GitHub API request failed: " + ex.getMessage(), ex);
        }
    }

    private static String encode(String value) {
        return URI.create("http://x/" + value).getRawPath().substring(1);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }
}
