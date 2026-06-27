package com.huawei.ascend.examples.workmate.mention;

import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.artifact.ArtifactService;
import com.huawei.ascend.examples.workmate.artifact.dto.FileContentResponse;
import com.huawei.ascend.examples.workmate.connector.ConnectorService;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.mention.MentionType;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import com.huawei.ascend.examples.workmate.office.SkillRegistry;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MentionContextService {

    private static final int MAX_MENTIONS = 10;
    private static final int MAX_FILE_CHARS = 4000;
    private static final int MAX_SKILL_CHARS = 2000;

    private final ArtifactService artifactService;
    private final SkillRegistry skillRegistry;
    private final ExpertRegistry expertRegistry;
    private final ConnectorService connectorService;
    private final CapabilityUsageService capabilityUsageService;

    public MentionContextService(
            ArtifactService artifactService,
            SkillRegistry skillRegistry,
            ExpertRegistry expertRegistry,
            ConnectorService connectorService,
            CapabilityUsageService capabilityUsageService) {
        this.artifactService = artifactService;
        this.skillRegistry = skillRegistry;
        this.expertRegistry = expertRegistry;
        this.connectorService = connectorService;
        this.capabilityUsageService = capabilityUsageService;
    }

    public String buildPromptSection(UUID sessionId, String expertId, List<MentionItem> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        int count = 0;
        for (MentionItem mention : mentions) {
            if (count >= MAX_MENTIONS) {
                break;
            }
            String block = resolveBlock(sessionId, expertId, mention);
            if (!block.isBlank()) {
                blocks.add(block);
                count++;
            }
        }
        if (blocks.isEmpty()) {
            return "";
        }
        return """
                User-attached context (mentions). Use when relevant; do not repeat verbatim.

                %s
                """.formatted(String.join("\n\n", blocks));
    }

    public boolean hasMemberMentions(List<MentionItem> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return false;
        }
        for (MentionItem mention : mentions) {
            if (mention.mentionType() == MentionType.MEMBER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strong leader instruction after a completed team round: delegate via {@code team.send_message}
     * to each @mentioned member instead of answering on their behalf.
     */
    public String buildTeamFollowUpDelegationSection(String expertId, List<MentionItem> mentions) {
        if (expertId == null || expertId.isBlank() || mentions == null || mentions.isEmpty()) {
            return "";
        }
        ExpertDefinition expert = expertRegistry.findExpert(expertId).orElse(null);
        if (expert == null || expert.members() == null) {
            return "";
        }
        List<String> memberLines = new ArrayList<>();
        for (MentionItem mention : mentions) {
            if (mention.mentionType() != MentionType.MEMBER) {
                continue;
            }
            for (TeamMemberDefinition member : expert.members()) {
                if (mention.id().equals(member.id()) || mention.id().equals(member.expertId())) {
                    String label = mention.label() != null && !mention.label().isBlank()
                            ? mention.label()
                            : member.name();
                    String role = member.role() != null ? member.role() : "";
                    memberLines.add("- memberId=%s, name=%s%s"
                            .formatted(
                                    member.id(),
                                    label,
                                    role.isBlank() ? "" : ", role=" + role));
                    break;
                }
            }
        }
        if (memberLines.isEmpty()) {
            return "";
        }
        return """
                Team follow-up delegation (required)
                The prior team round has completed. The user @mentioned specific member(s) and wants them to continue work.

                You MUST:
                1. Delegate to EACH listed member using team.send_message (target = member id), carrying the user's request.
                2. Do NOT perform the member's specialized work yourself.
                3. After member handbacks, synthesize an updated answer when appropriate.

                Mentioned members:
                %s
                """
                .formatted(String.join("\n", memberLines));
    }

    public List<Map<String, Object>> toPayload(List<MentionItem> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MentionItem mention : mentions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", mention.type().toLowerCase());
            map.put("id", mention.id());
            if (mention.path() != null && !mention.path().isBlank()) {
                map.put("path", mention.path());
            }
            if (mention.label() != null && !mention.label().isBlank()) {
                map.put("label", mention.label());
            }
            out.add(map);
        }
        return out;
    }

    private String resolveBlock(UUID sessionId, String expertId, MentionItem mention) {
        return switch (mention.mentionType()) {
            case FILE -> resolveFile(sessionId, mention);
            case SKILL -> resolveSkill(mention);
            case MEMBER -> resolveMember(expertId, mention);
            case CONNECTOR -> resolveConnector(mention);
        };
    }

    private String resolveFile(UUID sessionId, MentionItem mention) {
        String path = mention.path() != null && !mention.path().isBlank() ? mention.path() : mention.id();
        FileContentResponse file = artifactService.readFile(sessionId, path);
        String content = file.content() == null ? "" : file.content().trim();
        if (content.length() > MAX_FILE_CHARS) {
            content = content.substring(0, MAX_FILE_CHARS - 1) + "…";
        }
        String label = mention.label() != null && !mention.label().isBlank() ? mention.label() : path;
        return "[File: %s]\n%s".formatted(label, content);
    }

    private String resolveSkill(MentionItem mention) {
        SkillDefinition skill = skillRegistry.findSkill(mention.id()).orElse(null);
        if (skill == null) {
            return "";
        }
        capabilityUsageService.recordUsage("skill", skill.id());
        String label = mention.label() != null && !mention.label().isBlank() ? mention.label() : skill.name();
        String body = skill.skillBody() != null && !skill.skillBody().isBlank()
                ? skill.skillBody()
                : skill.description();
        if (body.length() > MAX_SKILL_CHARS) {
            body = body.substring(0, MAX_SKILL_CHARS - 1) + "…";
        }
        return "[Skill: %s]\n%s".formatted(label, body);
    }

    private String resolveMember(String expertId, MentionItem mention) {
        if (expertId == null || expertId.isBlank()) {
            return "";
        }
        ExpertDefinition expert = expertRegistry.findExpert(expertId).orElse(null);
        if (expert == null || expert.members() == null) {
            return "";
        }
        for (TeamMemberDefinition member : expert.members()) {
            if (mention.id().equals(member.id()) || mention.id().equals(member.expertId())) {
                String label = mention.label() != null && !mention.label().isBlank()
                        ? mention.label()
                        : member.name();
                String role = member.role() != null ? member.role() : "";
                return "[Team member: %s%s]\nFocus requests on this member's specialty when relevant."
                        .formatted(label, role.isBlank() ? "" : " (" + role + ")");
            }
        }
        return "";
    }

    private String resolveConnector(MentionItem mention) {
        ConnectorResponse connector = connectorService.listConnectors().stream()
                .filter(item -> mention.id().equals(item.id()))
                .findFirst()
                .orElse(null);
        if (connector == null) {
            return "";
        }
        capabilityUsageService.recordUsage("connector", connector.id());
        String label = mention.label() != null && !mention.label().isBlank()
                ? mention.label()
                : connector.name();
        return "[Connector: %s]\n%s (status: %s)"
                .formatted(label, connector.description(), connector.status());
    }
}
