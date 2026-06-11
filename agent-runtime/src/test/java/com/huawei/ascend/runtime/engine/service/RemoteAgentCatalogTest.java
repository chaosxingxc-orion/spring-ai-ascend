package com.huawei.ascend.runtime.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;

class RemoteAgentCatalogTest {

    @Test
    void refreshDiscoversCardAndBuildsToolSpecAndEndpoint() {
        RemoteAgentCatalog catalog = new RemoteAgentCatalog(List.of("http://remote-runtime"),
                url -> AgentCard.builder()
                        .name("Remote Planner")
                        .description("Plans trips")
                        .version("1")
                        .url("http://remote-runtime/a2a")
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

        catalog.refreshPending();

        assertThat(catalog.availableToolSpecs()).hasSize(1);
        RemoteAgentCatalog.RemoteAgentToolSpec spec = catalog.availableToolSpecs().get(0);
        assertThat(spec.remoteAgentId()).isEqualTo("remote-planner");
        assertThat(spec.toolName()).isEqualTo("a2a_remote_remote_planner");
        assertThat(spec.description()).contains("Remote Planner", "Plans trips", "Create a step-by-step plan");
        assertThat(spec.inputSchema()).containsEntry("type", "object");
        assertThat(catalog.endpoint("remote-planner")).isEqualTo("http://remote-runtime/a2a");
    }

    @Test
    void failedRefreshKeepsUrlPendingAndDoesNotExposeTool() {
        RemoteAgentCatalog catalog = new RemoteAgentCatalog(List.of("http://missing"), url -> {
            throw new IllegalStateException("not ready");
        });

        catalog.refreshPending();

        assertThat(catalog.availableToolSpecs()).isEmpty();
        assertThat(catalog.pendingUrls()).containsExactly("http://missing");
    }

    @Test
    void relativeJsonRpcEndpointIsResolvedAgainstConfiguredRuntimeUrl() {
        RemoteAgentCatalog catalog = new RemoteAgentCatalog(List.of("http://remote-runtime"),
                url -> AgentCard.builder()
                        .name("Remote B")
                        .description("Remote B")
                        .version("1")
                        .url("/a2a")
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

        catalog.refreshPending();

        assertThat(catalog.endpoint("remote-b")).isEqualTo("http://remote-runtime/a2a");
    }

    @Test
    void equivalentRuntimeAndCardUrlsRegisterOnlyOneRemoteTool() {
        RemoteAgentCatalog catalog = new RemoteAgentCatalog(List.of(
                "http://remote-runtime",
                "http://remote-runtime/",
                "http://remote-runtime/.well-known/agent-card.json"),
                url -> AgentCard.builder()
                        .name("Remote B")
                        .description("Remote B")
                        .version("1")
                        .url("/a2a")
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

        catalog.refreshPending();

        assertThat(catalog.availableToolSpecs()).hasSize(1);
        assertThat(catalog.pendingUrls()).isEmpty();
    }

    @Test
    void differentRuntimeUrlsWithSameCardNameExposeDistinctRemoteToolsAndEndpoints() {
        RemoteAgentCatalog catalog = new RemoteAgentCatalog(List.of("http://remote-a", "http://remote-b"),
                url -> remoteCard("Shared Remote", url + "/a2a"));

        catalog.refreshPending();

        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentCatalog.RemoteAgentToolSpec::remoteAgentId)
                .containsExactly("shared-remote", "shared-remote-2");
        assertThat(catalog.availableToolSpecs())
                .extracting(RemoteAgentCatalog.RemoteAgentToolSpec::toolName)
                .containsExactly("a2a_remote_shared_remote", "a2a_remote_shared_remote_2");
        assertThat(catalog.endpoint("shared-remote")).isEqualTo("http://remote-a/a2a");
        assertThat(catalog.endpoint("shared-remote-2")).isEqualTo("http://remote-b/a2a");
    }

    private static AgentCard remoteCard(String name, String endpoint) {
        return AgentCard.builder()
                .name(name)
                .description(name)
                .version("1")
                .url(endpoint)
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
