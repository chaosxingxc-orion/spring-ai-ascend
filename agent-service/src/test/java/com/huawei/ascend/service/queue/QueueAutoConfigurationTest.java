package com.huawei.ascend.service.queue;

import com.huawei.ascend.service.access.config.AccessLayerConfiguration;
import com.huawei.ascend.service.access.egress.EgressQueueRegistry;
import com.huawei.ascend.service.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.service.engine.queue.InMemoryEngineQueueGateway;
import com.huawei.ascend.service.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.service.taskcontrol.TaskControlService;
import com.huawei.ascend.service.taskcontrol.config.TaskControlAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueueAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    QueueAutoConfiguration.class,
                    TaskControlAutoConfiguration.class,
                    AccessLayerConfiguration.class,
                    EngineAutoConfiguration.class);

    @Test
    void serviceRuntimeUsesOneSharedQueueManagerBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QueueManager.class);
            assertThat(context).hasSingleBean(TaskControlService.class);
            assertThat(context).hasSingleBean(EgressQueueRegistry.class);
            assertThat(context).hasSingleBean(InMemoryEngineQueueGateway.class);
        });
    }
}
