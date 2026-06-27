package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.connector.ConnectorNotFoundException;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConnectorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final McpJsonConfigMapper mcpJsonConfigMapper;
    private final Map<String, ConnectorDefinition> connectors = new LinkedHashMap<>();

    public ConnectorRegistry(WorkmateOfficeProperties officeProperties, McpJsonConfigMapper mcpJsonConfigMapper) {
        this.mcpJsonConfigMapper = mcpJsonConfigMapper;
        reload(officeProperties.resolvedRoot());
    }

    void reload(Path officeRoot) {
        connectors.clear();
        loadFromDir(officeRoot.resolve("connectors-market"), true);
        LOG.info("Loaded {} office connector(s) from {}", connectors.size(), officeRoot);
    }

    public List<ConnectorDefinition> listConnectors() {
        return List.copyOf(connectors.values());
    }

    public Optional<ConnectorDefinition> findConnector(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(connectors.get(connectorId));
    }

    public ConnectorDefinition requireConnector(String connectorId) {
        return findConnector(connectorId).orElseThrow(() -> new ConnectorNotFoundException(connectorId));
    }

    private void loadFromDir(Path connectorsDir, boolean importedOnly) {
        LayeredOfficeAssetLoader.scanSubdirs(connectorsDir, dir -> loadConnectorDir(dir, importedOnly));
    }

    private void loadConnectorDir(Path connectorDir, boolean importedOnly) {
        Path yamlFile = connectorDir.resolve("connector.yaml");
        if (!Files.isRegularFile(yamlFile)) {
            return;
        }
        Optional<Map<?, ?>> rawOpt = LayeredOfficeAssetLoader.readYamlMap(yamlFile);
        if (rawOpt.isEmpty()) {
            return;
        }
        Map<?, ?> raw = rawOpt.get();
        String id = OfficeYaml.stringField(raw, "id");
        if (id == null || id.isBlank()) {
            LOG.warn("Skip connector.yaml without id: {}", yamlFile);
            return;
        }
        if (importedOnly && connectors.containsKey(id)) {
            return;
        }
        boolean defaultEnabled = OfficeYaml.booleanField(raw, "defaultEnabled", false);
        String mcpFile = OfficeYaml.stringField(raw, "mcpFile");
        Optional<McpServerConfig> parsed =
                mcpJsonConfigMapper.parse(id, connectorDir, mcpFile, defaultEnabled);
        boolean runnable = parsed.isPresent();
        ConnectorDefinition connector = new ConnectorDefinition(
                id,
                OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "name")),
                OfficeYaml.stringField(raw, "nameEn"),
                OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "description")),
                OfficeYaml.stringField(raw, "type"),
                OfficeYaml.stringField(raw, "authMethod"),
                OfficeYaml.stringField(raw, "source"),
                defaultEnabled,
                OfficeYaml.stringList(raw, "examples"),
                parsed.orElse(null),
                runnable);
        connectors.put(id, connector);
    }
}
