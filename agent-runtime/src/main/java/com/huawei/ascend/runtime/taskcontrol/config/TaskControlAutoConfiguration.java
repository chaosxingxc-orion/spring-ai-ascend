package com.huawei.ascend.runtime.taskcontrol.config;

import com.huawei.ascend.runtime.dispatch.api.EngineDispatchApi;
import com.huawei.ascend.runtime.queue.QueueManager;
import com.huawei.ascend.runtime.taskcontrol.EngineTaskControlAdapter;
import com.huawei.ascend.runtime.taskcontrol.TaskControlService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskControlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.runtime.taskcontrol.api.TaskControlClient.class)
    public TaskControlService taskControlService(QueueManager queueManager,
                                                 ObjectProvider<EngineDispatchApi> engineDispatchApi) {
        return new TaskControlService(queueManager, engineDispatchApi::getIfAvailable, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(com.huawei.ascend.runtime.dispatch.port.TaskControlClient.class)
    public EngineTaskControlAdapter engineTaskControlAdapter(TaskControlService taskControlService) {
        return new EngineTaskControlAdapter(taskControlService);
    }
}
