package com.huawei.ascend.service.queue.config;

import com.huawei.ascend.service.queue.QueueManager;
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
