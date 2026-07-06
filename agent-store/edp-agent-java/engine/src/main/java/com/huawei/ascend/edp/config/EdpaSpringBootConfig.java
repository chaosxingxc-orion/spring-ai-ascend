package com.huawei.ascend.edp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EDPAgent Spring Boot 配置模型。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>承载 application.yml 中 {@code edpa.agent.*} 下的配置，替代 edp-agent.yaml 直读。</li>
 *     <li>Spring Boot 原生支持 {@code ${ENV_VAR:default}} 占位符替换，不再需要手写环境变量覆盖逻辑。</li>
 *     <li>对外提供 ModelConfig / VersatileConfig，供 EdpaRuntimeHandler 和 EdpaAgentEnhancer 使用。</li>
 * </ul>
 *
 * <p>配置映射：</p>
 * <pre>
 * application.yml                  → EdpaSpringBootConfig
 * ─────────────────────────────────────────────────────
 * edpa.agent.model.provider        → model.provider
 * edpa.agent.model.name            → model.name
 * edpa.agent.model.base-url        → model.baseUrl
 * edpa.agent.model.api-key         → model.apiKey
 * edpa.agent.versatile.url         → versatile.url
 * edpa.agent.versatile.adapter-a2a-url → versatile.adapterA2aUrl
 * edpa.agent.versatile.timeout     → versatile.timeout
 * edpa.agent.versatile.url-variables   → versatile.urlVariables
 * edpa.agent.versatile.query-params    → versatile.queryParams
 * edpa.agent.versatile.headers     → versatile.headers
 * </pre>
 */
@ConfigurationProperties(prefix = "edpa.agent")
public class EdpaSpringBootConfig {

    /** 模型后端配置。 */
    private ModelConfig model;

    /** Versatile 服务配置。 */
    private VersatileConfig versatile;

    /** MCP SSE 连接配置。 */
    private McpSseConfig mcpsse;

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }

    public VersatileConfig getVersatile() { return versatile; }
    public void setVersatile(VersatileConfig versatile) { this.versatile = versatile; }

    public McpSseConfig getMcpsse() { return mcpsse; }
    public void setMcpsse(McpSseConfig mcpsse) { this.mcpsse = mcpsse; }

    /**
     * 模型后端配置。
     */
    public static class ModelConfig {
        /** 模型 provider，例如 openai-compatible。 */
        private String provider;

        /** 模型名称，例如 deepseek-v4-pro。 */
        private String name;

        /** 模型服务 baseUrl。 */
        private String baseUrl;

        /** 模型服务 API Key（支持环境变量 ${EDP_AGENT_MODEL_API_KEY:} 注入）。 */
        private String apiKey;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    /**
     * Versatile 服务配置。
     */
    public static class VersatileConfig {
        /** Versatile REST 直连地址，含 {conversation_id} 等路径占位符。 */
        private String url;

        /** adapter-versatile-agent-java 的 A2A SSE 入口。 */
        private String adapterA2aUrl;

        /** 调用超时。 */
        private String timeout = "30s";

        /** URL 路径变量占位。 */
        private Map<String, String> urlVariables = new LinkedHashMap<>();

        /** 查询参数。 */
        private Map<String, String> queryParams = new LinkedHashMap<>();

        /** 请求头。 */
        private Map<String, String> headers = new LinkedHashMap<>();

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getAdapterA2aUrl() { return adapterA2aUrl; }
        public void setAdapterA2aUrl(String adapterA2aUrl) { this.adapterA2aUrl = adapterA2aUrl; }

        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }

        public Map<String, String> getUrlVariables() { return urlVariables; }
        public void setUrlVariables(Map<String, String> urlVariables) {
            this.urlVariables = urlVariables != null ? urlVariables : new LinkedHashMap<>();
        }

        public Map<String, String> getQueryParams() { return queryParams; }
        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams != null ? queryParams : new LinkedHashMap<>();
        }

        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) {
            this.headers = headers != null ? headers : new LinkedHashMap<>();
        }
    }

    /**
     * MCP SSE 连接配置。
     */
    public static class McpSseConfig {
        /** MCP SSE 主 URL（灰度：wap_grayFlag 以 JD 开头时使用）。 */
        private String masterUrl;

        /** MCP SSE 备 URL（灰度：wap_grayFlag 非 JD 开头时使用）。 */
        private String standbyUrl;

        /** MCP SSE 鉴权 Token。 */
        private String accessToken;

        /** MCP SSE 应用名称。 */
        private String appName;

        public String getMasterUrl() { return masterUrl; }
        public void setMasterUrl(String masterUrl) { this.masterUrl = masterUrl; }

        public String getStandbyUrl() { return standbyUrl; }
        public void setStandbyUrl(String standbyUrl) { this.standbyUrl = standbyUrl; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
    }
}
