package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
 * Catalog of the remote A2A runtimes this runtime can call as tools.
 *
 * <p>Thread-safety model: a background refresher writes, execution threads read.
 * Entries are immutable snapshots and the whole map is replaced through a single
 * volatile write at the end of {@link #refresh()}, so every reader
 * ({@link #availableToolSpecs()}, {@link #pendingUrls()}, {@link #endpoint(String)},
 * {@link #streamTimeout(String)}) observes either the previous or the next
 * complete snapshot — never a torn combination of old and new fields — and never
 * blocks on in-flight card fetches.
 */
public class RemoteAgentCardCache {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteAgentCardCache.class);

    private final Function<String, AgentCard> cardResolver;
    private volatile Map<String, Entry> entries;

    public RemoteAgentCardCache(List<String> urls) {
        this(urls, Map.of());
    }

    public RemoteAgentCardCache(List<String> urls, Map<String, Duration> streamTimeoutsByUrl) {
        this(urls, streamTimeoutsByUrl, url -> new A2ACardResolver(url).getAgentCard());
    }

    RemoteAgentCardCache(List<String> urls, Function<String, AgentCard> cardResolver) {
        this(urls, Map.of(), cardResolver);
    }

    RemoteAgentCardCache(List<String> urls, Map<String, Duration> streamTimeoutsByUrl,
            Function<String, AgentCard> cardResolver) {
        this.cardResolver = Objects.requireNonNull(cardResolver, "cardResolver");
        Map<String, Duration> timeouts = streamTimeoutsByUrl == null ? Map.of() : streamTimeoutsByUrl;
        Set<String> unique = new LinkedHashSet<>(urls == null ? List.of() : urls);
        Map<String, Entry> initial = new LinkedHashMap<>();
        unique.stream()
                .filter(url -> url != null && !url.isBlank())
                .forEach(url -> initial.putIfAbsent(canonicalRuntimeKey(url), Entry.pending(url, timeouts.get(url))));
        this.entries = Collections.unmodifiableMap(initial);
    }

    /**
     * Full refresh: re-resolves the card of every configured url, pending or
     * available, so card/endpoint changes on a live remote propagate without a
     * redeploy. A failed re-resolve keeps an already-available entry serving its
     * last good card (degraded but callable); a never-resolved entry stays
     * pending. Synchronized so concurrent refreshes cannot interleave their
     * read-allocate-write of sticky ids; readers are not blocked — they keep
     * reading the previous volatile snapshot until the new one is published.
     */
    /**
     * Full refresh: re-resolves the card of every configured url.
     *
     * @return true when every configured url resolved successfully;
     *         false when at least one refresh attempt failed (the entry
     *         keeps its last good data, degraded but callable)
     */
    public synchronized boolean refresh() {
        Map<String, Entry> next = new LinkedHashMap<>();
        boolean allSucceeded = true;
        for (Map.Entry<String, Entry> mapEntry : entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            Entry refreshed;
            try {
                AgentCard card = cardResolver.apply(entry.url());
                refreshed = entry.withCard(card, endpoint(card, entry.url()));
            } catch (RuntimeException error) {
                LOG.warn("remote agent card refresh failed url={} errorClass={} message={}",
                        entry.url(), error.getClass().getSimpleName(), error.getMessage());
                refreshed = entry;
                allSucceeded = false;
            }
            next.put(mapEntry.getKey(), refreshed);
        }
        rebuildToolSpecs(next);
        entries = Collections.unmodifiableMap(next);
        return allSucceeded;
    }

    /** Reads the current volatile snapshot; see the class comment for the visibility model. */
    public List<RemoteAgentToolSpec> availableToolSpecs() {
        return entries.values().stream()
                .filter(Entry::available)
                .map(Entry::spec)
                .toList();
    }

    /** Reads the current volatile snapshot; see the class comment for the visibility model. */
    public List<String> pendingUrls() {
        return entries.values().stream()
                .filter(entry -> !entry.available())
                .map(Entry::url)
                .toList();
    }

    /** Reads the current volatile snapshot; see the class comment for the visibility model. */
    public String endpoint(String remoteAgentId) {
        return entries.values().stream()
                .filter(Entry::available)
                .filter(entry -> entry.remoteAgentId().equals(remoteAgentId))
                .map(Entry::endpoint)
                .findFirst()
                .orElse(null);
    }

    /**
     * Configured stream timeout for one available remote agent, or null when the
     * agent is unknown or carries no explicit timeout. Reads the current volatile
     * snapshot; see the class comment for the visibility model.
     */
    public Duration streamTimeout(String remoteAgentId) {
        return entries.values().stream()
                .filter(Entry::available)
                .filter(entry -> entry.remoteAgentId().equals(remoteAgentId))
                .map(Entry::streamTimeout)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static RemoteAgentToolSpec toSpec(String remoteAgentId, String desc) {
        return new RemoteAgentToolSpec(
                remoteAgentId,
                remoteAgentId,
                desc,
                inputSchema());
    }

    /**
     * Assigns remoteAgentIds sticky: an id allocated to an entry never changes on
     * later refreshes, because transport caches and parked tasks' route metadata
     * key on it — re-deduplicating by iteration order would silently re-route
     * them. Newly available entries only take suffixes that are still free.
     * The tool spec itself is rebuilt every time so a changed card propagates.
     */
    private static void rebuildToolSpecs(Map<String, Entry> next) {
        Set<String> taken = new HashSet<>();
        for (Entry entry : next.values()) {
            if (entry.resolved() && entry.remoteAgentId() != null) {
                taken.add(entry.remoteAgentId());
            }
        }
        for (Map.Entry<String, Entry> mapEntry : next.entrySet()) {
            Entry entry = mapEntry.getValue();
            if (!entry.resolved()) {
                continue;
            }
            // An agent without published skills has no callable tool contract.
            // The LLM would not know how to invoke it — skip tool registration.
            String desc = description(entry.card());
            if (desc.isEmpty()) {
                continue;
            }
            String remoteAgentId = entry.remoteAgentId();
            if (remoteAgentId == null) {
                remoteAgentId = allocateId(normalize(entry.card().name()), taken);
                taken.add(remoteAgentId);
            }
            mapEntry.setValue(entry.withAssignment(remoteAgentId, toSpec(remoteAgentId, desc)));
        }
    }

    private static String allocateId(String baseId, Set<String> taken) {
        if (!taken.contains(baseId)) {
            return baseId;
        }
        for (int count = 2; ; count++) {
            String candidate = baseId + "-" + count;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    private static String endpoint(AgentCard card, String configuredUrl) {
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

    /**
     * Build the tool description for the LLM from the card's published skills.
     *
     * <p>Only skill descriptions are used — they define the tool contract
     * (how to invoke, what parameters to pass). The card-level name and
     * description are infrastructure metadata that would confuse the LLM.
     * When an agent publishes no skills it is not exposed as a callable tool.
     */
    private static String description(AgentCard card) {
        List<String> parts = new ArrayList<>();
        if (card.skills() != null) {
            for (AgentSkill skill : card.skills()) {
                if (skill != null && hasText(skill.description())) {
                    parts.add(skill.description());
                }
            }
        }
        String result = String.join("\n", parts);
        LOG.info("remote agent tool description assembled name={} skillsCount={} totalChars={}\n{}",
                card.name(), card.skills() != null ? card.skills().size() : 0, result.length(), result);
        return result;
    }

    /**
     * Returns an open input schema that accepts arbitrary key-value pairs.
     * The remote agent's skill descriptions (from its AgentCard) tell the
     * LLM which specific fields to extract; the schema stays open so the
     * LLM can pass whatever business parameters the workflow needs.
     */
    private static Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", true);
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

    private static String canonicalRuntimeKey(String url) {
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

    /** Immutable snapshot of one configured remote; refresh replaces, never mutates. */
    private record Entry(
            String url,
            Duration streamTimeout,
            AgentCard card,
            String endpoint,
            String remoteAgentId,
            RemoteAgentToolSpec spec) {

        static Entry pending(String url, Duration streamTimeout) {
            return new Entry(url, streamTimeout, null, null, null, null);
        }

        Entry withCard(AgentCard card, String endpoint) {
            return new Entry(url, streamTimeout, card, endpoint, remoteAgentId, spec);
        }

        Entry withAssignment(String remoteAgentId, RemoteAgentToolSpec spec) {
            return new Entry(url, streamTimeout, card, endpoint, remoteAgentId, spec);
        }

        boolean resolved() {
            return card != null && endpoint != null;
        }

        boolean available() {
            return resolved() && remoteAgentId != null && spec != null;
        }
    }
}
