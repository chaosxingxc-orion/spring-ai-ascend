package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.connector.ConnectorService;
import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.office.dto.StudioRuntimeResponse;
import org.springframework.stereotype.Service;

@Service
public class StudioRuntimeService {

    private final ModelCatalogService modelCatalogService;
    private final McpGateway mcpGateway;
    private final ConnectorService connectorService;

    public StudioRuntimeService(
            ModelCatalogService modelCatalogService, McpGateway mcpGateway, ConnectorService connectorService) {
        this.modelCatalogService = modelCatalogService;
        this.mcpGateway = mcpGateway;
        this.connectorService = connectorService;
    }

    public StudioRuntimeResponse runtimeOverview() {
        return new StudioRuntimeResponse(
                modelCatalogService.catalog(),
                mcpGateway.listServers(),
                mcpGateway.listTools(),
                connectorService.listConnectors());
    }
}
