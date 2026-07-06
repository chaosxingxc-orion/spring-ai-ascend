# deploy/config/

- **`edp-config.yaml`**：路径锚点（供 `EDPA_AGENT_CONFIG_PATH` 解析 `/app/config/governance/`），不含业务配置。
- **model / versatile**：在 JAR 内 `application.yml` 的 `edpa.agent.*`，Docker 下通过环境变量覆盖。
- **治理配置**：`engine/src/main/resources/governance/`（构建时 COPY 到 `/app/config/governance/`）。
