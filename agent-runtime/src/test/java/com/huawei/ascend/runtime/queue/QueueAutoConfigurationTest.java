package com.huawei.ascend.service.queue;

import com.huawei.ascend.service.access.config.AccessLayerConfiguration;
import com.huawei.ascend.service.bootstrap.AgentServiceBootstrapConfiguration;
import com.huawei.ascend.service.engine.command.EngineCommandGateway;
import com.huawei.ascend.service.engine.command.EngineCommandProcessor;
import com.huawei.ascend.service.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.service.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.service.session.config.SessionManageConfiguration;
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
                    SessionManageConfiguration.class,
                    AgentServiceBootstrapConfiguration.class,
                    EngineAutoConfiguration.class);

    @Test
    void serviceRuntimeUsesOneSharedQueueManagerBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QueueManager.class);
            assertThat(context).hasSingleBean(TaskControlService.class);
            assertThat(context).hasSingleBean(EngineCommandGateway.class);
            assertThat(context).hasSingleBean(EngineCommandProcessor.class);
        });
    }
}
