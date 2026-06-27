package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.connector.ConnectorIds;
import com.huawei.ascend.examples.workmate.mcp.McpConnectorResolver;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertCapabilitiesResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertCapabilityItemResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StudioExpertCapabilitiesService {

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;
    private final McpConnectorResolver mcpConnectorResolver;

    public StudioExpertCapabilitiesService(
            ExpertRegistry expertRegistry, SkillRegistry skillRegistry, McpConnectorResolver mcpConnectorResolver) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
        this.mcpConnectorResolver = mcpConnectorResolver;
    }

    public StudioExpertCapabilitiesResponse resolve(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        ExpertDefinition expert = expertRegistry
                .findEntry(safeId)
                .map(ExpertRegistryEntry::definition)
                .orElseThrow(() -> new ExpertNotFoundException(safeId));

        Map<String, StudioExpertCapabilityItemResponse> skills = new LinkedHashMap<>();
        Map<String, StudioExpertCapabilityItemResponse> connectors = new LinkedHashMap<>();
        List<StudioExpertCapabilityItemResponse> unresolved = new ArrayList<>();

        for (String skillId : expert.preloadSkills()) {
            addSkill(skillId, skills, unresolved);
        }

        for (String rawId : expert.skillCompatibility()) {
            if (skills.containsKey(rawId) || connectors.containsKey(rawId)) {
                continue;
            }
            String connectorId = ConnectorIds.normalize(rawId);
            Optional<ConnectorDefinition> connector = mcpConnectorResolver.catalogConnectors().stream()
                    .filter(item -> item.id().equals(connectorId))
                    .findFirst();
            if (connector.isPresent()) {
                connectors.put(connectorId, fromConnector(connector.get()));
                continue;
            }
            if (addSkill(rawId, skills, null)) {
                continue;
            }
            if (!connectorId.equals(rawId) && addSkill(connectorId, skills, null)) {
                continue;
            }
            unresolved.add(new StudioExpertCapabilityItemResponse(rawId, rawId, null, false, null));
        }

        return new StudioExpertCapabilitiesResponse(
                List.copyOf(skills.values()), List.copyOf(connectors.values()), List.copyOf(unresolved));
    }

    private boolean addSkill(
            String skillId,
            Map<String, StudioExpertCapabilityItemResponse> skills,
            List<StudioExpertCapabilityItemResponse> unresolved) {
        Optional<SkillRegistryEntry> entry = skillRegistry.findEntry(skillId);
        if (entry.isEmpty()) {
            if (unresolved != null) {
                unresolved.add(new StudioExpertCapabilityItemResponse(skillId, skillId, null, false, null));
            }
            return false;
        }
        SkillRegistryEntry skill = entry.get();
        skills.put(skill.definition().id(), fromSkill(skill));
        return true;
    }

    private static StudioExpertCapabilityItemResponse fromSkill(SkillRegistryEntry entry) {
        SkillDefinition skill = entry.definition();
        return new StudioExpertCapabilityItemResponse(
                skill.id(),
                skill.name(),
                skill.description(),
                true,
                entry.source().name());
    }

    private static StudioExpertCapabilityItemResponse fromConnector(ConnectorDefinition connector) {
        return new StudioExpertCapabilityItemResponse(
                connector.id(),
                connector.name(),
                connector.description(),
                true,
                connector.source());
    }
}
