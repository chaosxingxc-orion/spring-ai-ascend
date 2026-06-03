package com.huawei.ascend.service.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Engine configuration (design §15.2). The openJiuwen LLM fields mirror the
 * framework's {@code apiconfig.json} keys; {@code apiKey} is a secret and should
 * be injected via Vault rather than written in plain yaml.
 */
@ConfigurationProperties(prefix = "agent-service.engine")
public class EngineProperties {

    private boolean enabled = true;
    private final OpenJiuwen openjiuwen = new OpenJiuwen();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OpenJiuwen getOpenjiuwen() {
        return openjiuwen;
    }

    public static class OpenJiuwen {
        private boolean enabled = false;
        private String modelProvider = "openai";
        private String apiKey;
        private String apiBase;
        private String modelName;
        private boolean sslVerify = true;
        private int maxIterations = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelProvider() {
            return modelProvider;
        }

        public void setModelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiBase() {
            return apiBase;
        }

        public void setApiBase(String apiBase) {
            this.apiBase = apiBase;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public boolean isSslVerify() {
            return sslVerify;
        }

        public void setSslVerify(boolean sslVerify) {
            this.sslVerify = sslVerify;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }
}
