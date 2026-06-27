package com.huawei.ascend.examples.workmate.market;

import com.huawei.ascend.examples.workmate.market.dto.AddMarketplaceRequest;
import com.huawei.ascend.examples.workmate.market.dto.MarketplaceDetailResponse;
import com.huawei.ascend.examples.workmate.market.dto.MarketplaceResponse;
import com.huawei.ascend.examples.workmate.market.dto.PluginResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    public MarketplaceController(MarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @GetMapping("/marketplaces")
    public List<MarketplaceResponse> listMarketplaces() {
        return marketplaceService.listMarketplaces();
    }

    @GetMapping("/marketplaces/{id}")
    public MarketplaceDetailResponse getMarketplace(@PathVariable String id) {
        return marketplaceService.getMarketplace(id);
    }

    @PostMapping("/marketplaces")
    public MarketplaceDetailResponse addMarketplace(@RequestBody AddMarketplaceRequest request) {
        return marketplaceService.addMarketplace(request);
    }

    @PostMapping("/marketplaces/{id}/refresh")
    public MarketplaceDetailResponse refreshMarketplace(@PathVariable String id) {
        return marketplaceService.refreshMarketplace(id);
    }

    @DeleteMapping("/marketplaces/{id}")
    public void deleteMarketplace(@PathVariable String id) {
        marketplaceService.deleteMarketplace(id);
    }

    @GetMapping("/plugins")
    public List<PluginResponse> listPlugins() {
        return marketplaceService.listPlugins();
    }

    @PostMapping("/plugins/{marketplaceId}/{pluginId}/install")
    public PluginResponse installPlugin(@PathVariable String marketplaceId, @PathVariable String pluginId) {
        return marketplaceService.installPlugin(marketplaceId, pluginId);
    }

    @PostMapping("/plugins/{marketplaceId}/{pluginId}/uninstall")
    public PluginResponse uninstallPlugin(@PathVariable String marketplaceId, @PathVariable String pluginId) {
        return marketplaceService.uninstallPlugin(marketplaceId, pluginId);
    }

    @PostMapping("/plugins/{marketplaceId}/{pluginId}/update")
    public PluginResponse updatePlugin(@PathVariable String marketplaceId, @PathVariable String pluginId) {
        return marketplaceService.updatePlugin(marketplaceId, pluginId);
    }
}
