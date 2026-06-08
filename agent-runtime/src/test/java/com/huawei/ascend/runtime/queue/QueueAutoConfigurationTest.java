package com.huawei.ascend.runtime.queue;

import com.huawei.ascend.runtime.access.AccessLayerConfiguration;
import com.huawei.ascend.runtime.app.RuntimeWiringConfiguration;
import com.huawei.ascend.runtime.engine.EngineCommandGateway;
import com.huawei.ascend.runtime.engine.EngineWorker;
import com.huawei.ascend.runtime.engine.EngineAutoConfiguration;
import com.huawei.ascend.runtime.session.SessionManageConfiguration;
import com.huawei.ascend.runtime.control.TaskControlService;
import com.huawei.ascend.runtime.control.TaskControlAutoConfiguration;
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
                    RuntimeWiringConfiguration.class,
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
