package com.huawei.ascend.examples.workmate.taskstarter;

import com.huawei.ascend.examples.workmate.config.WorkmateGithubProperties;
import com.huawei.ascend.examples.workmate.connector.ConnectorCredentialStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GithubCredentialResolver {

    private final ConnectorCredentialStore credentialStore;
    private final WorkmateGithubProperties githubProperties;

    public GithubCredentialResolver(
            ConnectorCredentialStore credentialStore, WorkmateGithubProperties githubProperties) {
        this.credentialStore = credentialStore;
        this.githubProperties = githubProperties;
    }

    public Optional<String> resolveToken() {
        Optional<String> connector = credentialStore.find("github").flatMap(headers -> {
            String bearer = headers.get("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                return Optional.of(bearer.substring(7).trim());
            }
            String token = headers.get("token");
            if (token != null && !token.isBlank()) {
                return Optional.of(token.trim());
            }
            return Optional.empty();
        });
        if (connector.isPresent()) {
            return connector;
        }
        String env = githubProperties.token();
        if (env != null && !env.isBlank()) {
            return Optional.of(env.trim());
        }
        return Optional.empty();
    }
}
