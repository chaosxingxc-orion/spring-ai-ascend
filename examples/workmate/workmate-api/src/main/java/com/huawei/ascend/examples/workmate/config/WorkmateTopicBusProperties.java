package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.topic-bus")
public class WorkmateTopicBusProperties {

    /**
     * {@code local-in-memory} (default) or {@code ascend-runtime} (fallback until upstream SPI ships).
     */
    private String provider = "local-in-memory";

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
