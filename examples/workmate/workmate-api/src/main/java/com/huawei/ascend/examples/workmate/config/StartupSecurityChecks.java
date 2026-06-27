package com.huawei.ascend.examples.workmate.config;

import com.huawei.ascend.examples.workmate.config.WorkmateAutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emits startup WARNings for insecure-but-convenient defaults so a local/demo config is not silently
 * carried into a real deployment. Intentionally non-fatal: this is an internal example app.
 */
@Component
public class StartupSecurityChecks {

    private static final Logger LOG = LoggerFactory.getLogger(StartupSecurityChecks.class);
    private static final String DEFAULT_DB_PASSWORD = "workmate";

    private final String dbPassword;
    private final String llmApiKey;
    private final WorkmateCloudProperties cloud;
    private final WorkmateOAuthProperties oauth;
    private final WorkmateStudioProperties studio;
    private final WorkmateAutomationProperties automation;
    private final Environment environment;

    public StartupSecurityChecks(
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${workmate.llm.api-key:}") String llmApiKey,
            WorkmateCloudProperties cloud,
            WorkmateOAuthProperties oauth,
            WorkmateStudioProperties studio,
            WorkmateAutomationProperties automation,
            Environment environment) {
        this.dbPassword = dbPassword;
        this.llmApiKey = llmApiKey;
        this.cloud = cloud;
        this.oauth = oauth;
        this.studio = studio;
        this.automation = automation;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        if (DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            LOG.warn("Datasource is using the built-in default password '{}'. Override "
                    + "WORKMATE_DB_PASSWORD before any non-local deployment.", DEFAULT_DB_PASSWORD);
        }
        if (llmApiKey == null || llmApiKey.isBlank() || llmApiKey.startsWith("sk-local")) {
            LOG.warn("LLM API key is unset/placeholder; agent runs will fail until WORKMATE_LLM_API_KEY "
                    + "is set in .env.local.");
        }
        if (isProductionProfile()) {
            warnIfDemoFeatureEnabled("workmate.cloud.enabled", cloud.enabled(),
                    "Set WORKMATE_CLOUD_ENABLED=false or use spring.profiles.active=production.");
            warnIfDemoFeatureEnabled("workmate.oauth.mock-enabled", oauth.mockEnabled(),
                    "Set WORKMATE_OAUTH_MOCK_ENABLED=false for real OAuth redirect integrations.");
            warnIfDemoFeatureEnabled("workmate.studio.enabled", studio.enabled(),
                    "Set WORKMATE_STUDIO_ENABLED=false to lock down authoring APIs.");
        } else {
            if (cloud.enabled()) {
                LOG.info("Cloud sessions enabled (local stub). Disable with WORKMATE_CLOUD_ENABLED=false "
                        + "or spring.profiles.active=production for hardened deployments.");
            }
            if (oauth.mockEnabled()) {
                LOG.info("OAuth mock redirect enabled at /oauth/mock-authorize (dogfood only). "
                        + "Disable with WORKMATE_OAUTH_MOCK_ENABLED=false in production.");
            }
        }
        automation.webhooks().forEach((channel, config) -> {
            if (config.enabled()) {
                LOG.warn("Inbound automation webhook '{}' is ENABLED. Ensure a strong secret is configured "
                        + "and the endpoint is not exposed to the public internet.", channel);
                if (config.secret() == null || config.secret().isBlank()) {
                    LOG.error("Webhook channel '{}' is enabled but has no secret — requests will be rejected.",
                            channel);
                }
            }
        });
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private static void warnIfDemoFeatureEnabled(String key, boolean enabled, String hint) {
        if (enabled) {
            LOG.warn("{} is enabled under the production profile. {}", key, hint);
        }
    }
}
