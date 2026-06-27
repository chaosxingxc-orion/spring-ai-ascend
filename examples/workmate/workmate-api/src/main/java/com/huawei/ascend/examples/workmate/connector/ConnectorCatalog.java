package com.huawei.ascend.examples.workmate.connector;

import java.util.Map;
import java.util.Optional;

public final class ConnectorCatalog {

    private ConnectorCatalog() {
    }

    public record ConnectorMeta(
            String name, String description, boolean requiresAuth, String authHint, ConnectorAuthMethod authMethod) {
    }

    private static final Map<String, ConnectorMeta> METADATA = Map.of(
            "qieman",
            new ConnectorMeta(
                    "盈米 MCP",
                    "基金检索与详情（SearchFunds、BatchGetFundsDetail 等，W9 allowlist）",
                    true,
                    "盈米 Stargate x-api-key",
                    ConnectorAuthMethod.DEVICE_CODE),
            "docs-fs",
            new ConnectorMeta(
                    "本地文档 FS",
                    "通过 MCP filesystem 读取 WorkMate 设计文档",
                    false,
                    null,
                    ConnectorAuthMethod.NONE),
            "oa",
            new ConnectorMeta(
                    "模拟 OA",
                    "内部 OA 授信审批（submit_credit_memo，dogfood / UAT3）",
                    false,
                    null,
                    ConnectorAuthMethod.NONE),
            "github",
            new ConnectorMeta(
                    "GitHub",
                    "任务启动器：搜索仓库、克隆与分支切换（PAT / OAuth 重定向 dogfood）",
                    true,
                    "GitHub Personal Access Token",
                    ConnectorAuthMethod.REDIRECT));

    public static Optional<ConnectorMeta> find(String connectorId) {
        return Optional.ofNullable(METADATA.get(connectorId));
    }

    public static String displayName(String connectorId) {
        return find(connectorId).map(ConnectorMeta::name).orElse(connectorId);
    }

    public static String displayDescription(String connectorId) {
        return find(connectorId).map(ConnectorMeta::description).orElse("MCP 连接器");
    }
}
