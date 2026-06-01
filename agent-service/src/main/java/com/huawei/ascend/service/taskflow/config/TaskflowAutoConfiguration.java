package com.huawei.ascend.service.taskflow.config;

import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.taskflow.control.EngineTaskControlAdapter;
import com.huawei.ascend.service.taskflow.control.TaskControlService;
import com.huawei.ascend.service.taskflow.queue.QueueManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskflowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QueueManager taskflowQueueManager() {
        return new QueueManager();
    }

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.service.taskflow.control.api.TaskControlClient.class)
    public TaskControlService taskControlService(QueueManager queueManager,
                                                 ObjectProvider<EngineDispatchApi> engineDispatchApi) {
        return new TaskControlService(queueManager, engineDispatchApi::getIfAvailable, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.service.engine.spi.TaskControlClient.class)
    public EngineTaskControlAdapter engineTaskControlAdapter(TaskControlService taskControlService) {
        return new EngineTaskControlAdapter(taskControlService);
    }
}
