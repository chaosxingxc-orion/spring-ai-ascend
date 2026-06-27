package com.huawei.ascend.examples.workmate.security;

import com.huawei.ascend.examples.workmate.approval.ToolRiskPolicy;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.mcp.McpToolIdNaming;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SecurityPolicyService {

    private static final Pattern URL_IN_COMMAND =
            Pattern.compile("(https?://[^\\s\"'<>|;&]+)", Pattern.CASE_INSENSITIVE);

    private final SecurityPolicyStore store;
    private final WorkmateMcpProperties mcpProperties;

    public SecurityPolicyService(SecurityPolicyStore store, WorkmateMcpProperties mcpProperties) {
        this.store = store;
        this.mcpProperties = mcpProperties;
    }

    @PostConstruct
    void bind() {
        ToolRiskPolicy.bind(this);
    }

    public SecurityPolicyDefinition getPolicy() {
        return store.get();
    }

    public SecurityPolicyDefinition updatePolicy(SecurityPolicyDefinition policy) {
        store.save(policy);
        return store.get();
    }

    public SecurityPolicyDefinition resetPolicy() {
        store.reset();
        return store.get();
    }

    public ToolRiskPolicy.RiskAssessment overlay(
            ToolRiskPolicy.RiskAssessment base, String toolName, Map<String, Object> args) {
        if (base.requiresApproval()) {
            return base;
        }
        SecurityPolicyDefinition policy = store.get();
        if (toolName != null && toolName.contains("bash")) {
            String command = args.get("command") == null ? "" : String.valueOf(args.get("command"));
            for (String pattern : policy.bashBlockPatterns()) {
                if (matchesPattern(pattern, command)) {
                    return blocked("Blocked by security policy", command);
                }
            }
            Optional<String> blockedHost = firstBlockedHostInCommand(command);
            if (blockedHost.isPresent()) {
                return blocked("Blocked by security policy", blockedHost.get());
            }
            for (String pattern : policy.bashAskPatterns()) {
                if (matchesPattern(pattern, command)) {
                    return new ToolRiskPolicy.RiskAssessment(
                            "HIGH", "Requires approval by security policy", command);
                }
            }
        }
        if (WorkmateToolIds.isRead(toolName) || WorkmateToolIds.isWrite(toolName)) {
            String path = args.get("path") == null ? "" : String.valueOf(args.get("path"));
            for (String pattern : policy.fileBlockPatterns()) {
                if (matchesPattern(pattern, path)) {
                    return blocked("Blocked by security policy", path);
                }
            }
        }
        if (toolName != null && toolName.startsWith("mcp__")) {
            Optional<String> blockedHost = blockedMcpHost(toolName);
            if (blockedHost.isPresent()) {
                return blocked("Blocked by security policy", blockedHost.get());
            }
        }
        return base;
    }

    private Optional<String> blockedMcpHost(String toolName) {
        try {
            McpToolIdNaming.McpToolRef ref = McpToolIdNaming.parseOpenJiuwenToolId(toolName);
            return resolveMcpHost(ref.serverId()).filter(host -> !isDomainAllowed(host));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<String> resolveMcpHost(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return Optional.empty();
        }
        for (McpServerConfig server : mcpProperties.servers()) {
            if (!server.id().equals(serverId)) {
                continue;
            }
            if (server.url() == null || server.url().isBlank()) {
                return Optional.empty();
            }
            try {
                URI uri = URI.create(server.url());
                return Optional.ofNullable(uri.getHost());
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstBlockedHostInCommand(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = URL_IN_COMMAND.matcher(command);
        while (matcher.find()) {
            try {
                URI uri = URI.create(matcher.group(1));
                String host = uri.getHost();
                if (host != null && !isDomainAllowed(host)) {
                    return Optional.of(host);
                }
            } catch (RuntimeException ignored) {
                // skip malformed URL
            }
        }
        return Optional.empty();
    }

    private static ToolRiskPolicy.RiskAssessment blocked(String reason, String summary) {
        return new ToolRiskPolicy.RiskAssessment("CRITICAL", reason, summary);
    }

    public boolean isDomainAllowed(String host) {
        SecurityPolicyDefinition policy = store.get();
        if (host == null || host.isBlank()) {
            return true;
        }
        for (String deny : policy.domainDenyList()) {
            if (matchesPattern(deny, host)) {
                return false;
            }
        }
        if (policy.domainAllowList().isEmpty()) {
            return true;
        }
        for (String allow : policy.domainAllowList()) {
            if (matchesPattern(allow, host)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUrlAllowed(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        try {
            String host = URI.create(url).getHost();
            return host == null || isDomainAllowed(host);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static boolean matchesPattern(String pattern, String value) {
        if (pattern == null || pattern.isBlank() || value == null) {
            return false;
        }
        String trimmed = pattern.trim();
        if (trimmed.contains("*")) {
            String regex = trimmed.replace(".", "\\.").replace("*", ".*");
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).find();
        }
        return value.toLowerCase().contains(trimmed.toLowerCase());
    }
}
