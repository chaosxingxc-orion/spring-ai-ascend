package com.huawei.ascend.runtime.engine.a2a;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads remote A2A agent URLs from configuration, resolves their agent cards,
 * and caches the derived tool specs locally. Failed endpoints are retried on
 * each refresh cycle; cards for endpoints that become unreachable are evicted.
 * No long-lived connections — the card resolver is called once per refresh
 * and the underlying HTTP client is released.
 */
public class RemoteAgentCardCache {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteAgentCardCache.class);

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Function<String, AgentCard> cardResolver;

    public RemoteAgentCardCache(List<String> urls) {
        this(urls, url -> new A2ACardResolver(url).getAgentCard());
    }

    RemoteAgentCardCache(List<String> urls, Function<String, AgentCard> cardResolver) {
        this.cardResolver = Objects.requireNonNull(cardResolver, "cardResolver");
        Set<String> unique = new LinkedHashSet<>(urls == null ? List.of() : urls);
        unique.stream()
                .filter(url -> url != null && !url.isBlank())
                .forEach(url -> entries.putIfAbsent(canonicalKey(url), new Entry(url)));
        LOG.info("remote agent card cache initialized configuredUrls={} uniqueUrls={}",
                urls == null ? 0 : urls.size(), entries.size());
    }

    /** Attempt to resolve cards for every endpoint that is not yet available. */
    public void refresh() {
        int pending = pendingCount();
        if (pending == 0) {
            return;
        }
        LOG.info("remote agent card refresh starting pendingUrls={} totalUrls={}", pending, entries.size());
        int succeeded = 0;
        int failed = 0;
        for (Entry entry : entries.values()) {
            if (entry.available()) {
                continue;
            }
            try {
                LOG.info("remote agent card resolving url={}", entry.url);
                AgentCard card = cardResolver.apply(entry.url);
                entry.card = card;
                entry.endpoint = resolveEndpoint(card, entry.url);
                succeeded++;
            } catch (RuntimeException error) {
                LOG.warn("remote agent card refresh failed url={} errorClass={} message={}",
                        entry.url, error.getClass().getSimpleName(), error.getMessage());
                entry.card = null;
                entry.remoteAgentId = null;
                entry.spec = null;
                entry.endpoint = null;
                failed++;
            }
        }
        rebuildToolSpecs();
        LOG.info("remote agent card refresh complete succeeded={} failed={} availableTools={}",
                succeeded, failed, availableToolSpecs().size());
    }

    /** Attempt to re-resolve cards that previously failed. */
    public void refreshPending() {
        refresh();
    }

    private int pendingCount() {
        return (int) entries.values().stream().filter(entry -> !entry.available()).count();
    }

    public List<RemoteAgentToolSpec> availableToolSpecs() {
        return entries.values().stream()
                .filter(Entry::available)
                .map(entry -> entry.spec)
                .toList();
    }

    public List<String> pendingUrls() {
        return entries.values().stream()
                .filter(entry -> !entry.available())
                .map(entry -> entry.url)
                .toList();
    }

    public String endpoint(String remoteAgentId) {
        return entries.values().stream()
                .filter(Entry::available)
                .filter(entry -> entry.remoteAgentId.equals(remoteAgentId))
                .map(entry -> entry.endpoint)
                .findFirst()
                .orElse(null);
    }

    private static RemoteAgentToolSpec toSpec(AgentCard card, String remoteAgentId) {
        return new RemoteAgentToolSpec(
                remoteAgentId,
                "a2a_remote_" + remoteAgentId.replace('-', '_'),
                description(card),
                inputSchema());
    }

    private void rebuildToolSpecs() {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            if (entry.card == null || entry.endpoint == null) {
                if (entry.remoteAgentId != null) {
                    LOG.info("remote agent card evicted url={} previousAgentId={}", entry.url, entry.remoteAgentId);
                }
                entry.remoteAgentId = null;
                entry.spec = null;
                continue;
            }
            String baseId = normalize(entry.card.name());
            int count = seen.merge(baseId, 1, Integer::sum);
            String remoteAgentId = count == 1 ? baseId : baseId + "-" + count;
            entry.remoteAgentId = remoteAgentId;
            entry.spec = toSpec(entry.card, remoteAgentId);
            LOG.info("remote agent card resolved url={} agentId={} toolName={} endpoint={}",
                    entry.url, remoteAgentId, entry.spec.toolName(), entry.endpoint);
        }
    }

    private static String resolveEndpoint(AgentCard card, String configuredUrl) {
        if (card.supportedInterfaces() != null) {
            for (AgentInterface agentInterface : card.supportedInterfaces()) {
                if (agentInterface != null && isJsonRpc(agentInterface.protocolBinding())
                        && hasText(agentInterface.url())) {
                    return resolveUrl(configuredUrl, agentInterface.url());
                }
            }
        }
        return hasText(card.url()) ? resolveUrl(configuredUrl, card.url()) : configuredUrl;
    }

    private static boolean isJsonRpc(String protocolBinding) {
        return protocolBinding != null
                && protocolBinding.replace("_", "").replace("-", "").equalsIgnoreCase("JSONRPC");
    }

    private static String description(AgentCard card) {
        List<String> parts = new ArrayList<>();
        if (hasText(card.name())) {
            parts.add(card.name());
        }
        if (hasText(card.description())) {
            parts.add(card.description());
        }
        if (card.skills() != null) {
            for (AgentSkill skill : card.skills()) {
                if (skill != null && hasText(skill.description())) {
                    parts.add(skill.description());
                }
            }
        }
        return String.join("\n", parts);
    }

    private static Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of(
                                "type", "string",
                                "description", "Input message sent to the remote A2A runtime.")),
                "required", List.of("message"));
    }

    private static String normalize(String value) {
        String normalized = (hasText(value) ? value : "remote-agent")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "remote-agent" : normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String canonicalKey(String url) {
        URI uri = URI.create(url.trim());
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return withoutTrailingSlash(uri.toString());
        }
        if (path.endsWith("/.well-known/agent-card.json") || path.endsWith("/.well-known/agent.json")) {
            String rootPath = path.substring(0, path.indexOf("/.well-known/"));
            URI root = URI.create(uri.toString()).resolve(rootPath.isBlank() ? "/" : rootPath + "/");
            return withoutTrailingSlash(root.toString());
        }
        return withoutTrailingSlash(uri.toString());
    }

    private static String withoutTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String resolveUrl(String baseUrl, String candidate) {
        if (!hasText(candidate)) {
            return baseUrl;
        }
        URI uri = URI.create(candidate);
        if (uri.isAbsolute()) {
            return candidate;
        }
        String base = hasText(baseUrl) && baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(base).resolve(uri).toString();
    }

    public record RemoteAgentToolSpec(
            String remoteAgentId,
            String toolName,
            String description,
            Map<String, Object> inputSchema) {
    }

    private static final class Entry {
        private final String url;
        private AgentCard card;
        private String remoteAgentId;
        private RemoteAgentToolSpec spec;
        private String endpoint;

        private Entry(String url) {
            this.url = url;
        }

        private boolean available() {
            return card != null && remoteAgentId != null && spec != null && endpoint != null;
        }
    }
}
