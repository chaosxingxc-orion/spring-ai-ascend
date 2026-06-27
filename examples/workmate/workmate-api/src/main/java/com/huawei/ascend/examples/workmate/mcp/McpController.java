package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.mcp.McpGateway.McpServerSummary;
import com.huawei.ascend.examples.workmate.mcp.McpGateway.McpToolDescriptor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private final McpGateway gateway;
    private final McpConnectionService connectionService;

    public McpController(McpGateway gateway, McpConnectionService connectionService) {
        this.gateway = gateway;
        this.connectionService = connectionService;
    }

    @GetMapping("/servers")
    public List<McpServerSummary> listServers() {
        return gateway.listServers();
    }

    @GetMapping("/tools")
    public List<McpToolDescriptor> listTools() {
        return gateway.listTools();
    }

    @PostMapping("/servers/{serverId}/reconnect")
    public McpServerSummary reconnect(@PathVariable String serverId) {
        return connectionService.reconnectById(serverId);
    }

    @PostMapping("/servers/{serverId}/disconnect")
    public McpServerSummary disconnect(@PathVariable String serverId) {
        return connectionService.disable(serverId);
    }
}
