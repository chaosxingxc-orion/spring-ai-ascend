package com.huawei.ascend.examples.workmate.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.artifact.dto.FileContentResponse;
import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.connector.ConnectorService;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import com.huawei.ascend.examples.workmate.office.SkillRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MentionContextServiceTest {

    @Mock
    private ArtifactService artifactService;

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private ExpertRegistry expertRegistry;

    @Mock
    private ConnectorService connectorService;

    @Mock
    private CapabilityUsageService capabilityUsageService;

    private MentionContextService service;

    @BeforeEach
    void setUp() {
        service = new MentionContextService(
                artifactService, skillRegistry, expertRegistry, connectorService, capabilityUsageService);
    }

    @Test
    void buildsPromptSectionForFileAndSkill() {
        UUID sessionId = UUID.randomUUID();
        when(artifactService.readFile(sessionId, "notes.md"))
                .thenReturn(new FileContentResponse("notes.md", "text/plain", "hello file", 10, false));
        when(skillRegistry.findSkill("fund-search"))
                .thenReturn(Optional.of(new SkillDefinition(
                        "fund-search",
                        "Fund Search",
                        "Search funds",
                        "finance",
                        List.of(),
                        "builtin",
                        true,
                        "Use concise fund summaries.")));

        String section = service.buildPromptSection(
                sessionId,
                null,
                List.of(
                        new MentionItem("file", "notes.md", "notes.md", "notes.md"),
                        new MentionItem("skill", "fund-search", null, "Fund Search")));

        assertThat(section).contains("hello file").contains("Use concise fund summaries");
    }

    @Test
    void toPayloadLowercasesType() {
        assertThat(service.toPayload(List.of(new MentionItem("SKILL", "x", null, "X"))))
                .first()
                .extracting(map -> map.get("type"))
                .isEqualTo("skill");
    }

    @Test
    void resolvesConnectorMetadata() {
        when(connectorService.listConnectors())
                .thenReturn(List.of(new ConnectorResponse(
                        "docs", "Docs", "Document connector", "connected", 2, false, null, 0, false, null)));

        String section = service.buildPromptSection(
                UUID.randomUUID(), null, List.of(new MentionItem("connector", "docs", null, "Docs")));

        assertThat(section).contains("Document connector").contains("connected");
    }

    @Test
    void hasMemberMentionsDetectsMemberType() {
        assertThat(service.hasMemberMentions(List.of(new MentionItem("file", "a.md", null, "A")))).isFalse();
        assertThat(service.hasMemberMentions(List.of(new MentionItem("member", "topic-researcher", null, "谭溯源"))))
                .isTrue();
    }

    @Test
    void buildsTeamFollowUpDelegationSectionForMentionedMembers() {
        when(expertRegistry.findExpert("gpt-researcher-team"))
                .thenReturn(Optional.of(new com.huawei.ascend.examples.workmate.office.ExpertDefinition(
                        "gpt-researcher-team",
                        "Research Team",
                        "desc",
                        "team",
                        "lead",
                        "init",
                        "cat",
                        List.of(),
                        List.of(),
                        List.of(new com.huawei.ascend.examples.workmate.office.TeamMemberDefinition(
                                "topic-researcher", "谭溯源", "topic-researcher", "research", 1, null)),
                        "sequential",
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        List.of(),
                        List.of(),
                        "openjiuwen-team",
                        null)));

        String section = service.buildTeamFollowUpDelegationSection(
                "gpt-researcher-team",
                List.of(new MentionItem("member", "topic-researcher", null, "谭溯源")));

        assertThat(section)
                .contains("Team follow-up delegation")
                .contains("team.send_message")
                .contains("memberId=topic-researcher")
                .contains("谭溯源");
    }
}
