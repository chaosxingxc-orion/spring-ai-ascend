package com.huawei.ascend.runtime.queue;

import com.huawei.ascend.runtime.access.config.AccessLayerConfiguration;
import com.huawei.ascend.runtime.bootstrap.AgentServiceBootstrapConfiguration;
import com.huawei.ascend.runtime.engine.command.EngineCommandGateway;
import com.huawei.ascend.runtime.engine.command.EngineWorker;
import com.huawei.ascend.runtime.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.runtime.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.runtime.session.config.SessionManageConfiguration;
import com.huawei.ascend.runtime.taskcontrol.TaskControlService;
import com.huawei.ascend.runtime.taskcontrol.config.TaskControlAutoConfiguration;
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
            assertThat(context).hasSingleBean(EngineWorker.class);
        });
    }
}
