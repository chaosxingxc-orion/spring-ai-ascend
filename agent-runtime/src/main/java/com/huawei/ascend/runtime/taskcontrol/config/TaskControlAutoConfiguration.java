package com.huawei.ascend.service.taskcontrol.config;

import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.queue.QueueManager;
import com.huawei.ascend.service.taskcontrol.EngineTaskControlAdapter;
import com.huawei.ascend.service.taskcontrol.TaskControlService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskControlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.service.taskcontrol.api.TaskControlClient.class)
    public TaskControlService taskControlService(QueueManager queueManager,
                                                 ObjectProvider<EngineDispatchApi> engineDispatchApi) {
        return new TaskControlService(queueManager, engineDispatchApi::getIfAvailable, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.service.engine.port.TaskControlClient.class)
    public EngineTaskControlAdapter engineTaskControlAdapter(TaskControlService taskControlService) {
        return new EngineTaskControlAdapter(taskControlService);
    }
}
