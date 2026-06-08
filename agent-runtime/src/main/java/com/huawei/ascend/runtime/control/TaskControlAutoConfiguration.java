package com.huawei.ascend.runtime.control;

import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
import com.huawei.ascend.runtime.engine.AccessLayerClient;
import com.huawei.ascend.runtime.queue.QueueManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskControlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.runtime.control.api.TaskControlApi.class)
    public TaskControlService taskControlService(QueueManager queueManager,
                                                 ObjectProvider<EngineExecutionApi> engineDispatchApi) {
        return new TaskControlService(queueManager, engineDispatchApi::getIfAvailable, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.runtime.engine.TaskControlClient.class)
    public EngineTaskControlAdapter engineTaskControlAdapter(TaskControlService taskControlService,
                                                            AccessLayerClient accessLayerClient) {
        return new EngineTaskControlAdapter(taskControlService, accessLayerClient);
    }
}
