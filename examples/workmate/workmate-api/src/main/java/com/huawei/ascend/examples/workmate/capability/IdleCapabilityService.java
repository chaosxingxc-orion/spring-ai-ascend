package com.huawei.ascend.examples.workmate.capability;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.connector.ConnectorService;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.mcp.McpConnectionService;
import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.office.SkillInstallStore;
import com.huawei.ascend.examples.workmate.office.SkillRegistry;
import com.huawei.ascend.examples.workmate.office.SkillService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IdleCapabilityService {

    private static final long DEFAULT_IDLE_DAYS = 30;

    private final CapabilityUsageService usageService;
    private final SkillRegistry skillRegistry;
    private final SkillInstallStore skillInstallStore;
    private final ConnectorService connectorService;
    private final McpGateway mcpGateway;
    private final McpConnectionService mcpConnectionService;
    private final SkillService skillService;
    private final WorkmateMcpProperties mcpProperties;

    public IdleCapabilityService(
            CapabilityUsageService usageService,
            SkillRegistry skillRegistry,
            SkillInstallStore skillInstallStore,
            ConnectorService connectorService,
            McpGateway mcpGateway,
            McpConnectionService mcpConnectionService,
            SkillService skillService,
            WorkmateMcpProperties mcpProperties) {
        this.usageService = usageService;
        this.skillRegistry = skillRegistry;
        this.skillInstallStore = skillInstallStore;
        this.connectorService = connectorService;
        this.mcpGateway = mcpGateway;
        this.mcpConnectionService = mcpConnectionService;
        this.skillService = skillService;
        this.mcpProperties = mcpProperties;
    }

    public List<IdleCapabilityItem> listIdle(long idleDays) {
        long thresholdDays = idleDays > 0 ? idleDays : DEFAULT_IDLE_DAYS;
        Instant cutoff = Instant.now().minus(Duration.ofDays(thresholdDays));
        List<IdleCapabilityItem> idle = new ArrayList<>();

        skillInstallStore.installedIds().stream()
                .map(skillRegistry::findSkill)
                .flatMap(java.util.Optional::stream)
                .forEach(skill -> {
                    Instant lastUsed = usageService.lastUsed("skill", skill.id());
                    if (lastUsed != null && lastUsed.isBefore(cutoff)) {
                        idle.add(new IdleCapabilityItem(
                                "skill",
                                skill.id(),
                                skill.name(),
                                lastUsed,
                                daysSince(lastUsed)));
                    }
                });

        for (ConnectorResponse connector : connectorService.listConnectors()) {
            if (!"connected".equalsIgnoreCase(connector.status())) {
                continue;
            }
            Instant lastUsed = usageService.lastUsed("connector", connector.id());
            if (lastUsed != null && lastUsed.isBefore(cutoff)) {
                idle.add(new IdleCapabilityItem(
                        "connector",
                        connector.id(),
                        connector.name(),
                        lastUsed,
                        daysSince(lastUsed)));
            }
        }

        if (mcpProperties.enabled()) {
            for (McpGateway.McpServerSummary server : mcpGateway.listServers()) {
                if (!server.connected()) {
                    continue;
                }
                Instant lastUsed = usageService.lastUsed("mcp", server.serverId());
                if (lastUsed != null && lastUsed.isBefore(cutoff)) {
                    idle.add(new IdleCapabilityItem(
                            "mcp",
                            server.serverId(),
                            server.serverId(),
                            lastUsed,
                            daysSince(lastUsed)));
                }
            }
        }

        idle.sort(Comparator.comparingLong(IdleCapabilityItem::idleDays).reversed());
        return idle;
    }

    public void disable(String type, String id) {
        if (type == null || id == null || id.isBlank()) {
            throw new IllegalArgumentException("type and id are required");
        }
        switch (type) {
            case "skill" -> skillService.uninstall(id);
            case "connector" -> connectorService.disconnect(id);
            case "mcp" -> mcpConnectionService.disable(id);
            default -> throw new IllegalArgumentException("Unsupported capability type: " + type);
        }
    }

    private static long daysSince(Instant instant) {
        if (instant == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(instant, Instant.now()).toDays();
    }
}
