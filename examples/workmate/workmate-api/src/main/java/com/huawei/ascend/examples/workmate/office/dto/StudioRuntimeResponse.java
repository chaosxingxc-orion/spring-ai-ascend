package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.mcp.McpGateway.McpServerSummary;
import com.huawei.ascend.examples.workmate.mcp.McpGateway.McpToolDescriptor;
import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse;
import java.util.List;

public record StudioRuntimeResponse(
        ModelCatalogResponse models,
        List<McpServerSummary> mcpServers,
        List<McpToolDescriptor> mcpTools,
        List<ConnectorResponse> connectors) {}
