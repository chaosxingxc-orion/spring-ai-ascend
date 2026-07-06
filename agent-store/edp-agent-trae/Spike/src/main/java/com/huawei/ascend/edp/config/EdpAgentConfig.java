package com.huawei.ascend.edp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * edp-agent.yaml 标准配置模型。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>承载 EDPAgent 标准 YAML 配置的反序列化结果。</li>
 *     <li>向 {@code EdpaRuntimeHandler} 提供模型、Prompt 和框架选项。</li>
 *     <li>作为 DeepAgentConfig 构造过程的输入模型。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>各字段 getter/setter：供 Jackson 反序列化和业务代码读取。</li>
 *     <li>{@link Framework}：框架级配置。</li>
 *     <li>{@link Options}：框架运行选项。</li>
 *     <li>{@link Model}：模型后端配置。</li>
 *     <li>{@link Prompt}：系统提示词配置。</li>
 * </ul>
 */
public class EdpAgentConfig {

    /** Agent 名称。 */
    private String name;

    /** Agent 描述。 */
    private String description;

    /** 框架配置节点。 */
    private Framework framework;

    /** 模型配置节点。 */
    private Model model;

    /** Versatile 服务配置节点。 */
    private Versatile versatile;

    /** Prompt 配置节点。 */
    private Prompt prompt;

    /** Skill 配置节点。 */
    private Skills skills;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Framework getFramework() { return framework; }
    public void setFramework(Framework framework) { this.framework = framework; }

    public Model getModel() { return model; }
    public void setModel(Model model) { this.model = model; }

    public Versatile getVersatile() { return versatile; }
    public void setVersatile(Versatile versatile) { this.versatile = versatile; }

    public Prompt getPrompt() { return prompt; }
    public void setPrompt(Prompt prompt) { this.prompt = prompt; }

    public Skills getSkills() { return skills; }
    public void setSkills(Skills skills) { this.skills = skills; }

    /**
     * 框架配置节点。
     */
    public static class Framework {
        /** 框架运行选项。 */
        private Options options;

        public Options getOptions() { return options; }
        public void setOptions(Options options) { this.options = options; }
    }

    /**
     * 框架运行选项。
     */
    public static class Options {
        /** ReAct 最大迭代次数。 */
        private int maxIterations;

        /** 是否启用 task loop。 */
        private boolean enableTaskLoop;

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

        public boolean isEnableTaskLoop() { return enableTaskLoop; }
        public void setEnableTaskLoop(boolean enableTaskLoop) { this.enableTaskLoop = enableTaskLoop; }
    }

    /**
     * 模型后端配置。
     */
    public static class Model {
        /** 模型 provider，例如 openai-compatible。 */
        private String provider;

        /** 模型名称，例如 deepseek-v4-pro。 */
        private String name;

        /** 模型服务 baseUrl。 */
        private String baseUrl;

        /** 模型服务 API Key。 */
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
    public static class Versatile {
        private String url;
        private String timeout = "30s";
        @JsonProperty("url_variables")
        private Map<String, String> urlVariables = new LinkedHashMap<>();
        @JsonProperty("query_params")
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private Map<String, String> headers = new LinkedHashMap<>();

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

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
     * Skill 配置。
     */
    public static class Skills {
        private List<String> directories = new ArrayList<>();
        private String mode = "all";

        public List<String> getDirectories() { return directories; }
        public void setDirectories(List<String> directories) {
            this.directories = directories != null ? directories : new ArrayList<>();
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    /**
     * Prompt 配置。
     */
    public static class Prompt {
        /** 系统提示词。 */
        private String system;

        public String getSystem() { return system; }
        public void setSystem(String system) { this.system = system; }
    }
}
