package com.huawei.ascend.runtime.queue;

import com.huawei.ascend.runtime.access.config.AccessLayerConfiguration;
import com.huawei.ascend.runtime.bootstrap.AgentServiceBootstrapConfiguration;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandGateway;
import com.huawei.ascend.runtime.dispatch.command.EngineCommandProcessor;
import com.huawei.ascend.runtime.dispatch.config.EngineAutoConfiguration;
import com.huawei.ascend.runtime.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.runtime.session.config.SessionManageConfiguration;
import com.huawei.ascend.runtime.taskcontrol.TaskControlService;
import com.huawei.ascend.runtime.taskcontrol.config.TaskControlAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

// Spring Boot 4.0.5 is not thread-safe during ApplicationContext boot: two
// context-booting test classes booting concurrently in one reused Surefire fork
// can deadlock. This shared READ_WRITE lock serialises context boots across
// classes while non-context tests keep running in parallel. Any new
// context-booting test class MUST declare the same lock key.
@ResourceLock(value = "spring-context-boot", mode = ResourceAccessMode.READ_WRITE)
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
