package com.huawei.ascend.runtime.queue.config;

import com.huawei.ascend.runtime.queue.QueueManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueueManager queueManager() {
        return new QueueManager();
    }
}
