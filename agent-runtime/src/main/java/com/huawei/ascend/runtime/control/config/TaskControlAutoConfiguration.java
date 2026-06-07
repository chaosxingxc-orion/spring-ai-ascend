package com.huawei.ascend.runtime.control.config;

import com.huawei.ascend.runtime.engine.api.EngineExecutionApi;
import com.huawei.ascend.runtime.engine.port.AccessLayerClient;
import com.huawei.ascend.runtime.queue.QueueManager;
import com.huawei.ascend.runtime.control.EngineTaskControlAdapter;
import com.huawei.ascend.runtime.control.TaskControlService;
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
    @ConditionalOnMissingBean(com.huawei.ascend.runtime.engine.port.TaskControlClient.class)
    public EngineTaskControlAdapter engineTaskControlAdapter(TaskControlService taskControlService,
                                                            AccessLayerClient accessLayerClient) {
        return new EngineTaskControlAdapter(taskControlService, accessLayerClient);
    }
}
