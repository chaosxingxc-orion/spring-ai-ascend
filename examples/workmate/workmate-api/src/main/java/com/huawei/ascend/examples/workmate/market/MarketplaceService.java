package com.huawei.ascend.examples.workmate.market;

import com.huawei.ascend.examples.workmate.market.dto.AddMarketplaceRequest;
import com.huawei.ascend.examples.workmate.market.dto.MarketplaceDetailResponse;
import com.huawei.ascend.examples.workmate.market.dto.MarketplaceResponse;
import com.huawei.ascend.examples.workmate.market.dto.PluginResponse;
import com.huawei.ascend.examples.workmate.office.SkillDefinition;
import com.huawei.ascend.examples.workmate.office.SkillRegistry;
import com.huawei.ascend.examples.workmate.office.SkillService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceService {

    private static final String BUILTIN_ID = "workmate-builtin";

    private final MarketplaceStore marketplaceStore;
    private final PluginInstallStore pluginInstallStore;
    private final SkillRegistry skillRegistry;
    private final SkillService skillService;

    public MarketplaceService(
            MarketplaceStore marketplaceStore,
            PluginInstallStore pluginInstallStore,
            SkillRegistry skillRegistry,
            SkillService skillService) {
        this.marketplaceStore = marketplaceStore;
        this.pluginInstallStore = pluginInstallStore;
        this.skillRegistry = skillRegistry;
        this.skillService = skillService;
    }

    @PostConstruct
    void seedBuiltinMarketplace() {
        if (marketplaceStore.find(BUILTIN_ID).isEmpty()) {
            marketplaceStore.save(buildBuiltinMarketplace());
        } else {
            marketplaceStore.save(refreshBuiltinPlugins());
        }
    }

    public List<MarketplaceResponse> listMarketplaces() {
        return marketplaceStore.list().stream().map(MarketplaceResponse::from).toList();
    }

    public MarketplaceDetailResponse getMarketplace(String marketplaceId) {
        return MarketplaceDetailResponse.from(requireMarketplace(marketplaceId));
    }

    public MarketplaceDetailResponse addMarketplace(AddMarketplaceRequest request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new IllegalArgumentException("Marketplace id required");
        }
        if (marketplaceStore.find(request.id()).isPresent()) {
            throw new IllegalArgumentException("Marketplace already exists: " + request.id());
        }
        MarketplaceDefinition marketplace = new MarketplaceDefinition(
                request.id().trim(),
                blankToDefault(request.name(), request.id()),
                blankToDefault(request.sourceType(), "directory"),
                request.sourceUri(),
                false,
                List.of());
        marketplaceStore.save(marketplace);
        return MarketplaceDetailResponse.from(marketplace);
    }

    public MarketplaceDetailResponse refreshMarketplace(String marketplaceId) {
        if (BUILTIN_ID.equals(marketplaceId)) {
            MarketplaceDefinition refreshed = refreshBuiltinPlugins();
            marketplaceStore.save(refreshed);
            return MarketplaceDetailResponse.from(refreshed);
        }
        MarketplaceDefinition current = requireMarketplace(marketplaceId);
        return MarketplaceDetailResponse.from(current);
    }

    public void deleteMarketplace(String marketplaceId) {
        marketplaceStore.delete(marketplaceId);
    }

    public List<PluginResponse> listPlugins() {
        List<PluginResponse> plugins = new ArrayList<>();
        for (MarketplaceDefinition marketplace : marketplaceStore.list()) {
            for (PluginDefinition plugin : marketplace.plugins()) {
                boolean installed = pluginInstallStore.isInstalled(marketplace.id(), plugin.id());
                plugins.add(PluginResponse.from(marketplace.id(), marketplace.name(), plugin, installed));
            }
        }
        return plugins;
    }

    public PluginResponse installPlugin(String marketplaceId, String pluginId) {
        MarketplaceDefinition marketplace = requireMarketplace(marketplaceId);
        PluginDefinition plugin = requirePlugin(marketplace, pluginId);
        if (BUILTIN_ID.equals(marketplaceId)) {
            skillService.install(pluginId);
            return PluginResponse.from(marketplace.id(), marketplace.name(), plugin, true);
        }
        pluginInstallStore.install(marketplaceId, pluginId, plugin.version());
        return PluginResponse.from(marketplace.id(), marketplace.name(), plugin, true);
    }

    public PluginResponse uninstallPlugin(String marketplaceId, String pluginId) {
        MarketplaceDefinition marketplace = requireMarketplace(marketplaceId);
        PluginDefinition plugin = requirePlugin(marketplace, pluginId);
        if (plugin.policyLocked()) {
            throw new IllegalStateException("Policy locked plugin cannot be removed: " + pluginId);
        }
        if (BUILTIN_ID.equals(marketplaceId)) {
            skillService.uninstall(pluginId);
            return PluginResponse.from(marketplace.id(), marketplace.name(), plugin, false);
        }
        pluginInstallStore.uninstall(marketplaceId, pluginId);
        return PluginResponse.from(marketplace.id(), marketplace.name(), plugin, false);
    }

    public PluginResponse updatePlugin(String marketplaceId, String pluginId) {
        if (BUILTIN_ID.equals(marketplaceId)) {
            marketplaceStore.save(refreshBuiltinPlugins());
        }
        MarketplaceDefinition marketplace = requireMarketplace(marketplaceId);
        PluginDefinition plugin = requirePlugin(marketplace, pluginId);
        if (BUILTIN_ID.equals(marketplaceId)) {
            skillService.install(pluginId);
            PluginDefinition updated = new PluginDefinition(
                    plugin.id(),
                    plugin.name(),
                    plugin.description(),
                    plugin.version(),
                    plugin.category(),
                    plugin.policyLocked(),
                    false);
            return PluginResponse.from(marketplace.id(), marketplace.name(), updated, true);
        }
        pluginInstallStore.install(marketplaceId, pluginId, plugin.version());
        PluginDefinition updated = new PluginDefinition(
                plugin.id(),
                plugin.name(),
                plugin.description(),
                plugin.version(),
                plugin.category(),
                plugin.policyLocked(),
                false);
        return PluginResponse.from(marketplace.id(), marketplace.name(), updated, true);
    }

    private MarketplaceDefinition requireMarketplace(String marketplaceId) {
        return marketplaceStore
                .find(marketplaceId)
                .orElseThrow(() -> new MarketplaceNotFoundException(marketplaceId));
    }

    private static PluginDefinition requirePlugin(MarketplaceDefinition marketplace, String pluginId) {
        return marketplace.plugins().stream()
                .filter(plugin -> plugin.id().equals(pluginId))
                .findFirst()
                .orElseThrow(() -> new PluginNotFoundException(marketplace.id(), pluginId));
    }

    private MarketplaceDefinition buildBuiltinMarketplace() {
        return new MarketplaceDefinition(
                BUILTIN_ID,
                "WorkMate 内置技能",
                "builtin",
                "office/skills",
                true,
                buildBuiltinPlugins());
    }

    private MarketplaceDefinition refreshBuiltinPlugins() {
        MarketplaceDefinition current = marketplaceStore.find(BUILTIN_ID).orElse(buildBuiltinMarketplace());
        return new MarketplaceDefinition(
                current.id(),
                current.name(),
                current.sourceType(),
                current.sourceUri(),
                true,
                buildBuiltinPlugins());
    }

    private List<PluginDefinition> buildBuiltinPlugins() {
        return skillRegistry.listSkills().stream()
                .map(this::toPluginDefinition)
                .toList();
    }

    private PluginDefinition toPluginDefinition(SkillDefinition skill) {
        String installedVersion = pluginInstallStore
                .find(BUILTIN_ID, skill.id())
                .map(PluginInstallStore.InstalledPlugin::version)
                .orElse(null);
        String version = "1.0.0";
        boolean updateAvailable = installedVersion != null && !installedVersion.equals(version);
        return new PluginDefinition(
                skill.id(),
                skill.name(),
                skill.description(),
                version,
                skill.category(),
                skill.defaultInstalled(),
                updateAvailable);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
