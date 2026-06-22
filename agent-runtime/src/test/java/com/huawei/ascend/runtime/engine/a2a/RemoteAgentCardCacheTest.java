package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;

class RemoteAgentCardCacheTest {

    @Test
    void refreshDiscoversCardAndBuildsToolSpecAndEndpoint() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-runtime"),
                url -> AgentCard.builder()
                        .name("Remote Planner")
                        .description("Plans trips")
                        .version("1")
                        .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://remote-runtime/a2a")))
                        .capabilities(AgentCapabilities.builder().streaming(true).build())
                        .skills(List.of(AgentSkill.builder()
                                .id("plan")
                                .name("Plan")
                                .description("Create a step-by-step plan")
                                .tags(List.of("planning"))
                                .build()))
                        .defaultInputModes(List.of("text"))
                        .defaultOutputModes(List.of("text"))
                        .build());

        catalog.refresh();

        assertThat(catalog.availableToolSpecs()).hasSize(1);
        RemoteAgentToolSpec spec = catalog.availableToolSpecs().get(0);
        assertThat(spec.remoteAgentId()).isEqualTo("remote-planner");
        assertThat(spec.toolName()).isEqualTo("remote-planner");
        assertThat(spec.description()).contains("Create a step-by-step plan");
        assertThat(spec.inputSchema()).containsEntry("type", "object");
        assertThat(spec.inputSchema()).containsEntry("required", List.of("remoteInput"));
        Object properties = spec.inputSchema().get("properties");
        assertThat(properties).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) properties).containsKey("remoteInput")).isTrue();
        assertThat(catalog.endpoint("remote-planner")).isEqualTo("http://remote-runtime/a2a");
    }

    @Test
    void failedRefreshKeepsUrlPendingAndDoesNotExposeTool() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://missing"), url -> {
            throw new IllegalStateException("not ready");
        });

        catalog.refresh();

        assertThat(catalog.availableToolSpecs()).isEmpty();
        assertThat(catalog.pendingUrls()).containsExactly("http://missing");
    }

    @Test
    void relativeJsonRpcEndpointIsResolvedAgainstConfiguredRuntimeUrl() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-runtime"),
                url -> AgentCard.builder()
                        .name("Remote B")
                        .description("Remote B")
                        .version("1")
                        .url("/legacy-a2a")
                        .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "/a2a")))
                        .capabilities(AgentCapabilities.builder().streaming(true).build())
                        .skills(List.of(AgentSkill.builder()
                                .id("b")
                                .name("B")
                                .description("Remote B skill")
                                .tags(List.of("remote"))
                                .build()))
                        .defaultInputModes(List.of("text"))
                        .defaultOutputModes(List.of("text"))
                        .build());

        catalog.refresh();

        assertThat(catalog.endpoint("remote-b")).isEqualTo("http://remote-runtime/a2a");
    }

    @Test
    void equivalentRuntimeAndCardUrlsRegisterOnlyOneRemoteTool() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of(
                "http://remote-runtime",
                "http://remote-runtime/",
                "http://remote-runtime/.well-known/agent-card.json"),
                url -> AgentCard.builder()
                        .name("Remote B")
                        .description("Remote B")
                        .version("1")
                        .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "/a2a")))
                        .capabilities(AgentCapabilities.builder().streaming(true).build())
                        .skills(List.of(AgentSkill.builder()
                                .id("b")
                                .name("B")
                                .description("Remote B skill")
                                .tags(List.of("remote"))
                                .build()))
                        .defaultInputModes(List.of("text"))
                        .defaultOutputModes(List.of("text"))
                        .build());

        catalog.refresh();

        assertThat(catalog.availableToolSpecs()).hasSize(1);
        assertThat(catalog.pendingUrls()).isEmpty();
    }

    @Test
    void differentRuntimeUrlsWithSameCardNameExposeDistinctRemoteToolsAndEndpoints() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-a", "http://remote-b"),
                url -> remoteCard("Shared Remote", url + "/a2a"));

        catalog.refresh();

        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentToolSpec::remoteAgentId)
                .containsExactly("shared-remote", "shared-remote-2");
        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentToolSpec::toolName)
                .containsExactly("shared-remote", "shared-remote-2");
        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-a/a2a");
        assertThat(catalog.endpoint("shared-remote-2")).isEqualTo("http://remote-b/a2a");
    }

    /**
     * The drift this pins down: with order-dependent re-deduplication, the entry
     * that became available FIRST would lose its id to an earlier-configured
     * same-named entry on the next refresh — re-routing its cached transport and
     * every parked task keyed on that id. Ids must stick to the entry they were
     * first allocated to.
     */
    @Test
    void remoteAgentIdSticksToFirstAvailableEntryAcrossRefreshes() {
        AtomicBoolean firstUrlReady = new AtomicBoolean(false);
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-a", "http://remote-b"),
                url -> {
                    if ("http://remote-a".equals(url) && !firstUrlReady.get()) {
                        throw new IllegalStateException("not ready yet");
                    }
                    return remoteCard("Shared Remote", url + "/a2a");
                });

        catalog.refresh();

        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentToolSpec::remoteAgentId)
                .containsExactly("shared-remote");
        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-b/a2a");

        firstUrlReady.set(true);
        catalog.refresh();

        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-b/a2a");
        assertThat(catalog.endpoint("shared-remote-2")).isEqualTo("http://remote-a/a2a");
    }

    @Test
    void reRefreshPropagatesCardAndEndpointChangeKeepingRemoteAgentId() {
        AtomicReference<AgentCard> card = new AtomicReference<>(remoteCard("Shared Remote", "http://remote-a/a2a"));
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-a"), url -> card.get());

        catalog.refresh();
        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-a/a2a");

        card.set(remoteCard("Shared Remote", "http://remote-a/a2a-v2"));
        catalog.refresh();

        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-a/a2a-v2");
        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentToolSpec::remoteAgentId)
                .containsExactly("shared-remote");
    }

    @Test
    void failedReRefreshKeepsLastGoodCardForAvailableEntry() {
        AtomicBoolean failing = new AtomicBoolean(false);
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-a"), url -> {
            if (failing.get()) {
                throw new IllegalStateException("remote went away");
            }
            return remoteCard("Shared Remote", url + "/a2a");
        });

        catalog.refresh();
        assertThat(catalog.availableToolSpecs()).hasSize(1);

        failing.set(true);
        catalog.refresh();

        assertThat(catalog.availableToolSpecs()).hasSize(1);
        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-a/a2a");
        assertThat(catalog.pendingUrls()).isEmpty();
    }

    @Test
    void configuredStreamTimeoutIsResolvableByRemoteAgentId() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(
                List.of("http://remote-a", "http://remote-b"),
                Map.of("http://remote-a", Duration.ofMinutes(2)),
                url -> remoteCard("Shared Remote", url + "/a2a"));

        catalog.refresh();

        assertThat(catalog.streamTimeout("shared-remote")).isEqualTo(Duration.ofMinutes(2));
        assertThat(catalog.streamTimeout("shared-remote-2")).isNull();
        assertThat(catalog.streamTimeout("unknown")).isNull();
    }

    @Test
    void cardUrlIsUsedOnlyWhenJsonRpcSupportedInterfaceIsAbsent() {
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-legacy"),
                url -> AgentCard.builder()
                        .name("Legacy Remote")
                        .description("Legacy Remote")
                        .version("1")
                        .url("/a2a")
                        .supportedInterfaces(List.of(new AgentInterface("GRPC", "/grpc")))
                        .capabilities(AgentCapabilities.builder().streaming(true).build())
                        .skills(List.of(AgentSkill.builder()
                                .id("legacy")
                                .name("Legacy")
                                .description("Legacy skill")
                                .tags(List.of("remote"))
                                .build()))
                        .defaultInputModes(List.of("text"))
                        .defaultOutputModes(List.of("text"))
                        .build());

        boolean refreshed = catalog.refresh();

        assertThat(refreshed).isTrue();
        assertThat(catalog.endpoint("legacy-remote")).isEqualTo("http://remote-legacy/a2a");
        assertThat(catalog.pendingUrls()).isEmpty();
    }

    private static AgentCard remoteCard(String name, String endpoint) {
        return AgentCard.builder()
                .name(name)
                .description(name)
                .version("1")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", endpoint)))
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .skills(List.of(AgentSkill.builder()
                        .id("skill")
                        .name("Skill")
                        .description(name + " skill")
                        .tags(List.of("remote"))
                        .build()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
